package com.randevu.backend.service;

import com.randevu.backend.entity.*;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.notification.AppointmentExpiredEvent;
import com.randevu.backend.repository.*;
import com.randevu.backend.service.AvailabilityCalculator.BusyInterval;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.Clock;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BusinessRepository businessRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final WorkingHourRepository workingHourRepository;
    private final BusinessClosureRepository businessClosureRepository;
    private final AvailabilityCalculator availabilityCalculator;
    private final StaffRepository staffRepository;
    private final StaffWorkingHourRepository staffWorkingHourRepository;
    private final AppointmentExpiryPolicy expiryPolicy;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public AppointmentService(AppointmentRepository appointmentRepository,
            BusinessRepository businessRepository,
            ServiceItemRepository serviceItemRepository,
            WorkingHourRepository workingHourRepository,
            BusinessClosureRepository businessClosureRepository,
            AvailabilityCalculator availabilityCalculator,
            StaffRepository staffRepository,
            StaffWorkingHourRepository staffWorkingHourRepository,
            AppointmentExpiryPolicy expiryPolicy,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.appointmentRepository = appointmentRepository;
        this.businessRepository = businessRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.workingHourRepository = workingHourRepository;
        this.businessClosureRepository = businessClosureRepository;
        this.availabilityCalculator = availabilityCalculator;
        this.staffRepository = staffRepository;
        this.staffWorkingHourRepository = staffWorkingHourRepository;
        this.expiryPolicy = expiryPolicy;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    // Yeni randevu oluşturur ve saat çakışmalarını kontrol eder.
    // @Transactional: tek başına yarış koşulunu ÇÖZMÜYOR (iki eşzamanlı
    // istek yine aynı anda "exists" kontrolünü geçebilir), ama metodun
    // ortasında bir hata olursa (örn. save() patlarsa) yarım kalan hiçbir
    // yan etkinin commit edilmemesini garanti ediyor — atomiklik için şart.
    // Asıl yarış koşulu garantisi AppointmentSlotIndexInitializer'daki
    // veritabanı kısıtlamasından geliyor; save() o kısıtlamayı ihlal ederse
    // burada DataIntegrityViolationException fırlar, GlobalExceptionHandler
    // bunu 409'a çevirir (bkz. o handler'daki açıklama).
    @Transactional
    public Appointment createAppointment(Appointment newAppointment) {

        // Eskiden burada businessId hiç doğrulanmıyordu — controller sadece
        // "new Business(); setId(...)" ile boş bir stub kuruyordu. Olmayan
        // bir businessId, hizmet ile işletmenin eşleşmediği hallere kadar
        // gitmeden önce, ilk elden gerçek bir Business yükleyip var olduğunu
        // doğruluyoruz.
        Long businessId = newAppointment.getBusiness().getId();
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        // Sahibi hesap silme talep etmis (Faz 3.9) bir isletmeye yeni randevu
        // alinamaz -- BusinessService.getBusinessById'deki "yok say" (404)
        // deseninden FARKLI olarak burada 409 (BusinessRuleException) daha
        // dogru: musteri zaten bu isletmenin sayfasindaydi (var oldugunu
        // biliyor), sorun "boyle bir isletme yok" degil "su an randevu
        // alinamiyor".
        if (business.getSuspendedAt() != null) {
            throw new BusinessRuleException("Bu işletme şu anda randevu kabul etmiyor.");
        }

        ServiceItem service = serviceItemRepository.findById(newAppointment.getServiceItem().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hizmet bulunamadı."));

        // Kritik doğrulama: secilen hizmet gercekten bu isletmeye mi ait?
        // Bu kontrol olmadan, businessId=1 ve serviceId=999 (baska bir
        // isletmenin hizmeti) gonderilerek randevu olusturulabiliyordu —
        // yanlis sureyle cakisma hesabi yapiliyor, yanlis isletmenin
        // inbox'inda yanlis fiyat/hizmet gorunuyordu.
        if (!service.getBusiness().getId().equals(businessId)) {
            throw new BusinessRuleException("Seçilen hizmet bu işletmeye ait değil.");
        }

        // Soft delete edilmiş (artık sunulmayan) bir hizmete randevu alınamaz.
        if (!service.isActive()) {
            throw new ResourceNotFoundException("Hizmet bulunamadı.");
        }

        newAppointment.setBusiness(business);
        newAppointment.setServiceItem(service);

        // Randevunun hangi durumda DOĞDUĞU bir iş kuralı, HTTP çevirisi değil.
        // Eskiden controller'da atanıyordu (AppointmentController.createAppointment)
        // ama orası bu kararı verebilecek bilgiye sahip değil: controller elinde
        // sadece "new Business(); setId(...)" şeklinde bir stub tutuyor, gerçek
        // Business'ı hiç yüklemiyor. Gerçek Business burada, yukarıda yükleniyor
        // -- karar da buraya ait.
        //
        // İşletme başına "otomatik onay" (bkz. CLAUDE.md karar tablosu): açıksa
        // talep İstek Kutusu'nu (PENDING) hiç görmeden doğrudan APPROVED doğar.
        // Bu SADECE başlangıç durumunu değiştiriyor -- çakışma/slot kontrolü
        // (aşağıda), randevu ufku, açık talep sınırı gibi HİÇBİR kural atlanmıyor;
        // "otomatik onay" sadece işletme sahibinin manuel onay adımını atlaması,
        // güvenlik/tutarlılık kontrollerini değil.
        newAppointment.setStatus(business.isAutoApprove() ? AppointmentStatus.APPROVED : AppointmentStatus.PENDING);

        // Zaman kaynagi DAIMA enjekte edilen Clock -- ciplak LocalDateTime.now()
        // yok (bkz. TimeConfig). Ayni "now" hem createdAt hem ufuk kontrolu
        // icin kullaniliyor ki ikisi arasinda mikrosaniyelik bir tutarsizlik
        // bile olusmasin.
        LocalDateTime now = LocalDateTime.now(clock);
        newAppointment.setCreatedAt(now);

        // Randevu ufku: onceden hicbir ust sinir yoktu (@Future sadece gecmisi
        // engelliyordu), yani 2099'a randevu alinabiliyordu. Sinirsiz ufuk hem
        // slotu aylarca kilitliyor hem de isletmenin taahhut edemeyecegi bir
        // tarihe randevu yaziyor (fiyat/personel/calisma saatleri degisir).
        if (expiryPolicy.isBeyondHorizon(newAppointment.getAppointmentDate(), now)) {
            throw new BusinessRuleException("Randevu tarihi en fazla "
                    + expiryPolicy.getBookingHorizon().toDays() + " gün ileriye alınabilir.");
        }

        // Ayni isletmede acik (cevaplanmamis) talep siniri. Isletme bazinda
        // cunku saldiri senaryosu "bir isletmenin takvimini doldurmak"; genel
        // bir sinir uc farkli isletmeden cevap bekleyen normal kullaniciyi da
        // cezalandirirdi. APPROVED sayilmiyor: duzenli musterinin mevcut
        // randevusu varken bir sonrakini almasi engellenmemeli.
        // Not: bu tek hesapli kotuye kullanimi sinirlar, coklu hesabi degil --
        // o kayit/IP bazli hiz limiti isi (Faz 3.5).
        int openRequests = appointmentRepository.countByCustomerIdAndBusinessIdAndStatus(
                newAppointment.getCustomer().getId(), businessId, AppointmentStatus.PENDING);
        if (openRequests >= expiryPolicy.getMaxOpenRequestsPerBusiness()) {
            throw new BusinessRuleException("Bu işletmede cevaplanmamış "
                    + expiryPolicy.getMaxOpenRequestsPerBusiness()
                    + " talebiniz var. Yeni talep için mevcutların sonuçlanmasını bekleyin.");
        }

        LocalDateTime newStart = newAppointment.getAppointmentDate();
        LocalDateTime newEnd = newStart.plusMinutes(service.getDurationInMinutes());

        // Secilen saat gercekten calisma saatleri icinde mi? Bu kontrol
        // olmadan, /available-slots'ta HIC GORUNMEYEN bir saate (kapali
        // gun, ozel tatil, mesai disi) /create'e DOGRUDAN istek atilarak
        // randevu alinabiliyordu — getAvailableTimeSlots'taki kontrolu
        // atlamak, API'yi dogrudan cagirmak kadar kolaydi.
        EffectiveHours hours = resolveWorkingHours(businessId, business, newStart.toLocalDate())
                .orElseThrow(() -> new BusinessRuleException("İşletme bu tarihte kapalı."));
        if (newStart.toLocalTime().isBefore(hours.openTime()) || newEnd.toLocalTime().isAfter(hours.closeTime())) {
            throw new BusinessRuleException("Seçilen saat işletmenin çalışma saatleri dışında.");
        }

        // staff opsiyonel. Doluysa (Faz 2.9 itibariyle: sadece ileride bir
        // UI staffId gonderirse) GERCEKTEN bu isletmeye ait, aktif ve
        // secilen hizmeti veren bir personel mi -- ucu de service/business
        // eslesme kontroluyle ayni gerekce: dogrulanmadan birakilirsa bir
        // musteri baska bir isletmenin personelini ya da isten ayrilmis
        // birini secebilirdi.
        Staff staff = null;
        if (newAppointment.getStaff() != null) {
            staff = staffRepository.findById(newAppointment.getStaff().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Personel bulunamadı."));
            if (!staff.getBusiness().getId().equals(businessId)) {
                throw new BusinessRuleException("Seçilen personel bu işletmeye ait değil.");
            }
            if (!staff.isActive()) {
                throw new ResourceNotFoundException("Personel bulunamadı.");
            }
            if (!staffOffersService(staff, service)) {
                throw new BusinessRuleException("Seçilen personel bu hizmeti vermiyor.");
            }
            newAppointment.setStaff(staff);
        } else {
            // Faz 2.9: musteri personel secmiyor/gormuyor (bkz. CLAUDE.md karar
            // tablosu) -- isletmenin bu hizmeti veren aktif personeli VARSA
            // gorunmez sekilde en az dolu, musait olana atanir. Boylece
            // "10 personeli olan isletme bile 1 kisilik kapasite gosteriyor"
            // bug'i (bkz. ROADMAP 2.9) hem burada hem getAvailableTimeSlots'ta
            // duzeliyor. Personeli olmayan (ya da bu hizmeti veren personeli
            // olmayan) isletmeler icin staff null kalir, eski (isletme
            // capinda) davranis birebir korunur.
            List<Staff> qualifyingStaff = getQualifyingStaff(businessId, service);
            if (!qualifyingStaff.isEmpty()) {
                LocalDateTime dayStart = newStart.toLocalDate().atStartOfDay();
                LocalDateTime dayEnd = dayStart.plusDays(1).minusNanos(1);
                staff = autoAssignStaff(qualifyingStaff, business, businessId, newStart.toLocalDate(),
                        newStart, newEnd, dayStart, dayEnd)
                        .orElseThrow(() -> new BusinessRuleException("Bu saat için uygun personel bulunamadı."));
                newAppointment.setStaff(staff);
            }
        }

        // Spam tıklama koruması + günlük çakışma taraması: personel
        // atanmışsa (yukarıda ya açıkça ya da Faz 2.9'daki otomatik atamayla
        // dolmuş olabilir) personel bazlı, atanmamışsa (işletmede bu hizmeti
        // veren personel hiç yoksa — eski davranış) işletme bazlı kontrol
        // edilir. Bu, autoAssignStaff'ın kendi kontrolünden SONRA tekrar
        // yapılıyor gibi görünse de bilerek — save() anına kadar geçen sürede
        // (ör. eşzamanlı bir başka istek) oluşabilecek yeni bir çakışmayı
        // yakalayan ikinci bir güvenlik katmanı; asıl garanti yine de DB'deki
        // partial unique index'te (bkz. V5 migration).
        List<AppointmentStatus> blockingStatuses = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);
        LocalDateTime startOfDay = newStart.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        boolean alreadyExists;
        List<Appointment> dailyAppointments;
        if (staff != null) {
            alreadyExists = appointmentRepository.existsByStaffIdAndAppointmentDateAndStatusIn(
                    staff.getId(), newAppointment.getAppointmentDate(), blockingStatuses);
            dailyAppointments = appointmentRepository.findByStaffIdAndAppointmentDateBetweenAndStatusIn(
                    staff.getId(), startOfDay, endOfDay, blockingStatuses);
        } else {
            alreadyExists = appointmentRepository.existsByBusinessIdAndAppointmentDateAndStatusIn(
                    businessId, newAppointment.getAppointmentDate(), blockingStatuses);
            dailyAppointments = appointmentRepository.findByBusinessIdAndAppointmentDateBetweenAndStatusIn(
                    businessId, startOfDay, endOfDay, blockingStatuses);
        }

        if (alreadyExists) {
            throw new BusinessRuleException("Bu saat için zaten bir randevu isteği mevcut!");
        }

        boolean isOverlapping = dailyAppointments.stream().anyMatch(existing -> {
            LocalDateTime existingStart = existing.getAppointmentDate();
            LocalDateTime existingEnd = existingStart.plusMinutes(existing.getServiceItem().getDurationInMinutes());

            return newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart);
        });

        if (isOverlapping) {
            throw new BusinessRuleException("Seçilen saat aralığında başka bir randevu bulunmaktadır.");
        }

        return appointmentRepository.save(newAppointment);
    }

    // İşletmenin onay bekleyen (PENDING) randevularını getirir — İstek Kutusu (Inbox).
    public List<Appointment> getPendingAppointmentsForBusiness(Long businessId) {
        return appointmentRepository.findByBusinessIdAndStatus(businessId, AppointmentStatus.PENDING);
    }

    // Belirli bir işletmeye ait tüm randevuları getirir.
    public List<Appointment> getBusinessAppointments(Long businessId) {
        return appointmentRepository.findByBusinessId(businessId);
    }

    // Belirli bir müşteriye ait tüm randevuları getirir.
    public List<Appointment> getCustomerAppointments(Long customerId) {
        return appointmentRepository.findByCustomerId(customerId);
    }

    // Randevu üzerinde işletme sahibi/müşterinin isteyebileceği eylemler.
    // Eskiden controller'da action bir String'di ("approve"/"reject"/"cancel")
    // ve equalsIgnoreCase zinciriyle karşılaştırılıyordu — yeni bir eylem
    // (örn. Faz 2.1'deki NO_SHOW) eklemek, o if/else zincirinin İÇİNİ
    // KESMEYİ gerektirirdi (OCP ihlali). Enum + switch ile yeni bir eylem
    // eklemek artık sadece yeni bir sabit + yeni bir case eklemek —
    // mevcut case'lere dokunulmuyor, derleyici de eksik case'i haber verir.
    public enum AppointmentAction {
        APPROVE, REJECT, CANCEL, NO_SHOW;

        public static AppointmentAction from(String value) {
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Geçersiz işlem: " + value);
            }
        }
    }

    // Randevunun durumunu, eylemi isteyen kullanıcının yetkisini ve
    // randevunun MEVCUT durumunu kontrol ederek değiştirir. Eskiden bu
    // mantığın hem yetki kontrolü hem randevu arama kısmı controller'da
    // duruyordu (AppointmentController doğrudan AppointmentRepository
    // kullanıyordu) — controller'ın işi HTTP çevirisi yapmak, veritabanına
    // erişmek servisin işi (SRP). Ayrıca eskiden randevunun ŞU ANKİ durumu
    // hiç kontrol edilmiyordu — REJECTED bir randevu tekrar approve
    // edilebiliyordu; artık her eylemin hangi durumdan başlayabileceği
    // açıkça tanımlı.
    @Transactional
    public Appointment changeStatus(Long appointmentId, AppointmentAction action, Long currentUserId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Randevu bulunamadı."));

        boolean isBusinessOwner = appointment.getBusiness().getOwner().getId().equals(currentUserId);
        boolean isCustomer = appointment.getCustomer().getId().equals(currentUserId);

        switch (action) {
            case APPROVE -> {
                requireOwner(isBusinessOwner, "Bu işlemi yalnızca işletme sahibi yapabilir.");
                requireBusinessActiveForOwnerAction(appointment);
                requireCurrentStatus(appointment, EnumSet.of(AppointmentStatus.PENDING),
                        "Sadece onay bekleyen randevular onaylanabilir.");
                appointment.setStatus(AppointmentStatus.APPROVED);
            }
            case REJECT -> {
                requireOwner(isBusinessOwner, "Bu işlemi yalnızca işletme sahibi yapabilir.");
                requireBusinessActiveForOwnerAction(appointment);
                requireCurrentStatus(appointment, EnumSet.of(AppointmentStatus.PENDING),
                        "Sadece onay bekleyen randevular reddedilebilir.");
                appointment.setStatus(AppointmentStatus.REJECTED);
            }
            case CANCEL -> {
                if (!isBusinessOwner && !isCustomer) {
                    throw new AccessDeniedException("Bu randevuyu iptal etme yetkiniz yok.");
                }
                requireCurrentStatus(appointment, EnumSet.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED),
                        "Sadece bekleyen veya onaylanmış randevular iptal edilebilir.");
                appointment.setStatus(AppointmentStatus.CANCELLED);
            }
            // Musteri gelmedi. Sadece isletme sahibi isaretleyebilir (musterinin
            // kendi kendine "gelmedim" demesi anlamsiz, ayrica Faz 2.6'daki
            // yorum garantisinin bir parcasi: NO_SHOW olan bir randevuya yorum
            // yapilamayacak -- bu yuzden bu isaretlemenin sadece isletme
            // tarafindan, gercekten olani yansitarak yapilmasi onemli).
            // Sadece APPROVED'dan gecerli: PENDING bir randevuya musteri zaten
            // gelmiş olamaz (henuz onaylanmamis), COMPLETED/REJECTED/CANCELLED
            // zaten terminal durumlar.
            case NO_SHOW -> {
                requireOwner(isBusinessOwner, "Bu işlemi yalnızca işletme sahibi yapabilir.");
                requireBusinessActiveForOwnerAction(appointment);
                requireCurrentStatus(appointment, EnumSet.of(AppointmentStatus.APPROVED),
                        "Sadece onaylanmış randevular 'gelmedi' olarak işaretlenebilir.");
                appointment.setStatus(AppointmentStatus.NO_SHOW);
            }
        }

        return appointmentRepository.save(appointment);
    }

    // Suresi gecmis (appointmentDate + hizmet suresi < su an) ve hala
    // APPROVED durumunda kalan randevulari COMPLETED'a cevirir. Sistem
    // tarafindan (Faz 2.2'deki @Scheduled job'dan) cagrilir -- changeStatus'un
    // aksine bir "currentUserId" yok, cunku bu bir kullanicinin degil,
    // zamanin tetikledigi bir gecis. Bu yuzden AppointmentAction'a COMPLETE
    // diye bir eylem EKLENMEDI: o enum sadece kullanicinin PUT
    // /{id}/{action} ile tetikleyebilecegi eylemler icin (bkz. o enum'un
    // uzerindeki aciklama) -- musterinin ya da isletme sahibinin "tamamla"
    // butonuna basmasi anlamli degil, tamamlanma sadece zaman gecmesiyle olur.
    //
    // Idempotentlik: sorgu her calistiginda sadece HALA APPROVED olan
    // randevulari getirir. Bir randevu bir kere COMPLETED olduktan sonra
    // bir sonraki calismada bu sorguya hic girmez -- ayri bir "son calisma
    // zamani" takibi gerekmiyor, restart'ta da guvenli.
    @Transactional
    public int completeElapsedAppointments() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Appointment> elapsed = appointmentRepository.findByStatus(AppointmentStatus.APPROVED).stream()
                .filter(a -> a.getAppointmentDate()
                        .plusMinutes(a.getServiceItem().getDurationInMinutes())
                        .isBefore(now))
                .toList();

        elapsed.forEach(a -> a.setStatus(AppointmentStatus.COMPLETED));
        appointmentRepository.saveAll(elapsed);
        return elapsed.size();
    }

    // Cevaplanmamis taleplerden suresi dolanlari EXPIRED'a cevirir.
    //
    // completeElapsedAppointments ile ayni desen: sorgu SADECE hala PENDING
    // olanlari getirdigi icin islem idempotent -- job yeniden calistiginda
    // daha once dusurulenler zaten eslesmiyor, ayrica "en son ne zaman
    // calisti" gibi bir durum tutmaya gerek kalmiyor.
    //
    // Dusme ani hesabi burada DEGIL, AppointmentExpiryPolicy'de. O sinif saf
    // ve saat kaynagi tasimiyor; "now"i buradan, enjekte edilen Clock'tan
    // aliyor (bkz. TimeConfig).
    //
    // Her dusen randevu icin AppointmentExpiredEvent yayinlaniyor (Faz 3.4).
    // Bu sinif NotificationPort'u, NotificationService'i, hatta boyle bir
    // dinleyici olup olmadigini HIC bilmiyor -- sadece "bu oldu" diyor.
    // AppointmentNotificationListener bunu AFTER_COMMIT ile dinleyip
    // musteriye bildirim gonderiyor; o gonderim basarisiz olsa bile bu
    // metodun commit ettigi EXPIRED gecisini ETKILEMEZ (event zaten commit
    // sonrasi tetikleniyor, bkz. o dinleyicideki gerekce).
    @Transactional
    public int expireStaleRequests() {
        LocalDateTime now = LocalDateTime.now(clock);

        List<Appointment> expired = appointmentRepository.findByStatus(AppointmentStatus.PENDING).stream()
                .filter(a -> expiryPolicy.isExpired(a.getCreatedAt(), a.getAppointmentDate(), now))
                .toList();

        expired.forEach(a -> a.setStatus(AppointmentStatus.EXPIRED));
        appointmentRepository.saveAll(expired);
        expired.forEach(a -> eventPublisher.publishEvent(
                new AppointmentExpiredEvent(a.getId(), a.getCustomer().getId())));
        return expired.size();
    }

    // Bir talebin ne zaman dusecegi -- API uzerinden hem musteriye hem
    // isletmeye gosteriliyor. Kural gizli bir mekanik olmamali: gosterilmezse
    // 5 gun once talep atan musteri randevudan 1 saat once "olmamis" diye
    // ogrenir ve baska yere de gidemez.
    public LocalDateTime expiresAt(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.PENDING || appointment.getCreatedAt() == null) {
            return null;
        }
        return expiryPolicy.expiresAt(appointment.getCreatedAt(), appointment.getAppointmentDate());
    }

    private void requireOwner(boolean isBusinessOwner, String message) {
        if (!isBusinessOwner) {
            throw new AccessDeniedException(message);
        }
    }

    // Faz 3.9: hesap silme talep etmiş (askıdaki) bir işletme sahibi artık
    // YENİ bir işletme kararı ALAMAZ (onay/red/gelmedi) -- bu OwnershipGuard.
    // assertOwnsActiveBusiness ile AYNI "salt okunur" ilkesi, sadece burası
    // OwnershipGuard'dan geçmiyor (changeStatus'un kendi sahiplik kontrolü
    // var, yukarıda). CANCEL'e BİLEREK uygulanmıyor: hem müşterinin kendi
    // randevusunu her zaman iptal edebilmesi gerekiyor hem de
    // AccountDeletionService'in kendi otomatik iptalleri (haber verme
    // payı/geri dönüş penceresi) bu metodu işletme sahibinin ID'siyle
    // çağırıyor -- CANCEL'i burada engellemek kendi silme akışımızı
    // kilitlerdi.
    private void requireBusinessActiveForOwnerAction(Appointment appointment) {
        if (appointment.getBusiness().getSuspendedAt() != null) {
            throw new BusinessRuleException(
                    "İşletmeniz hesap silme sürecinde olduğu için bu işlem yapılamıyor.");
        }
    }

    private void requireCurrentStatus(Appointment appointment, Set<AppointmentStatus> allowed, String message) {
        if (allowed.contains(appointment.getStatus())) {
            return;
        }

        // EXPIRED icin ozel mesaj. Kontrol burada, tek yerde: approve, reject,
        // cancel ve no_show'un DORDU de bu metottan geciyor, dolayisiyla
        // mesaji her eyleme ayri ayri eklemek gerekmiyor (ve biri unutulmuyor).
        //
        // Neden gerekli: zaman asimina ugramis bir talepte cagirana
        // "Sadece onay bekleyen randevular onaylanabilir" deniyordu -- teknik
        // olarak dogru ama GERCEK sebebi soylemiyor. Isletme sahibi istek
        // kutusunu acik birakip 10 dakika sonra onaya bastiginda tam bu
        // duruma dusuyor ve talebin neden kayboldugunu anlamiyor. Bu "kotu
        // sans" degil, normal kullanim.
        if (appointment.getStatus() == AppointmentStatus.EXPIRED) {
            throw new BusinessRuleException(
                    "Bu talep cevaplanmadığı için zaman aşımına uğradı, artık işlem yapılamaz.");
        }

        throw new BusinessRuleException(message);
    }

    // Müşterinin şu andan sonraki, hâlâ aktif (PENDING/APPROVED) randevularını
    // getirir. İptal/ret edilmiş bir randevu tarihi gelecekte olsa bile
    // burada görünmez -- profil özetindeki "yaklaşan randevu" sayısıyla
    // aynı tanımı kullanır (bkz. AppointmentStatus.ACTIVE_STATUSES).
    public List<Appointment> getUpcomingCustomerAppointments(Long customerId) {
        return appointmentRepository.findByCustomerIdAndAppointmentDateAfterAndStatusIn(
                customerId, LocalDateTime.now(clock), AppointmentStatus.ACTIVE_STATUSES);
    }

    // İşletmenin şu andan sonraki, hâlâ aktif (PENDING/APPROVED) randevularını
    // getirir. Aynı tanım için bkz. getUpcomingCustomerAppointments.
    public List<Appointment> getUpcomingBusinessAppointments(Long businessId) {
        return appointmentRepository.findByBusinessIdAndAppointmentDateAfterAndStatusIn(
                businessId, LocalDateTime.now(clock), AppointmentStatus.ACTIVE_STATUSES);
    }

    // Belirtilen gün için işletmenin ve hizmetin süresine uygun boş saat
    // dilimlerini hesaplar. Bu metodun işi artık sadece veriyi TOPLAMAK
    // (işletme/hizmet var mı, o gün açık mı, o günün meşgul aralıkları
    // neler) — asıl hesaplama AvailabilityCalculator'a devredildi (bkz.
    // o sınıftaki açıklama: JPA'dan bağımsız, test edilebilir).
    //
    // Faz 2.9: işletmenin bu hizmeti veren aktif personeli VARSA artık
    // personel bazlı (calculateForStaff, birden fazla personelin BİRLEŞİMİ)
    // hesaplanıyor -- eskiden burası hep tek-kaynaklı calculate()'ı
    // kullanıyordu, yani 10 personeli olan bir işletme bile pratikte "1
    // kişilik kapasite" gösteriyordu (bkz. ROADMAP 2.9'daki bug açıklaması).
    // Personeli olmayan (ya da bu hizmeti veren personeli olmayan)
    // işletmeler için davranış birebir korunuyor.
    public List<LocalTime> getAvailableTimeSlots(Long businessId, Long serviceId, LocalDate date) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Dükkan bulunamadı."));

        ServiceItem serviceItem = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Hizmet bulunamadı."));

        List<Staff> qualifyingStaff = getQualifyingStaff(businessId, serviceItem);
        if (!qualifyingStaff.isEmpty()) {
            List<AvailabilityCalculator.StaffAvailability> staffAvailabilities = buildStaffAvailabilities(
                    business, businessId, date, qualifyingStaff);
            if (staffAvailabilities.isEmpty()) {
                // Personel var ama o gun HICBIRI calismiyor (hepsi kapali/izinli).
                return List.of();
            }
            return availabilityCalculator.calculateForStaff(date, serviceItem.getDurationInMinutes(), staffAvailabilities)
                    .stream()
                    .map(AvailabilityCalculator.SlotAssignment::time)
                    .toList();
        }

        Optional<EffectiveHours> hours = resolveWorkingHours(businessId, business, date);
        if (hours.isEmpty()) {
            // O gun ozel kapanis var ya da WorkingHour'da isClosed=true —
            // musteriye bos liste donuyoruz (hata degil, sadece "bos" gibi).
            return List.of();
        }

        LocalDateTime startOfDay = date.atTime(hours.get().openTime());
        LocalDateTime endOfDay = date.atTime(hours.get().closeTime());

        List<AppointmentStatus> blockingStatuses = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);
        List<Appointment> dailyAppointments = appointmentRepository
                .findByBusinessIdAndAppointmentDateBetweenAndStatusIn(businessId, startOfDay, endOfDay,
                        blockingStatuses);

        List<BusyInterval> busyIntervals = dailyAppointments.stream()
                .map(app -> new BusyInterval(
                        app.getAppointmentDate(),
                        app.getAppointmentDate().plusMinutes(app.getServiceItem().getDurationInMinutes())))
                .toList();

        return availabilityCalculator.calculate(date, hours.get().openTime(), hours.get().closeTime(),
                serviceItem.getDurationInMinutes(), busyIntervals);
    }

    private record EffectiveHours(LocalTime openTime, LocalTime closeTime) {
    }

    // İşletmenin, verilen hizmeti veren aktif personelini döner. Boş liste
    // dönerse çağıran taraf (getAvailableTimeSlots, createAppointment)
    // eski (işletme çapında, personelsiz) davranışa düşer.
    private List<Staff> getQualifyingStaff(Long businessId, ServiceItem service) {
        return staffRepository.findByBusinessIdAndIsActiveTrue(businessId).stream()
                .filter(staff -> staffOffersService(staff, service))
                .toList();
    }

    private boolean staffOffersService(Staff staff, ServiceItem service) {
        return staff.getServices().stream().anyMatch(s -> s.getId().equals(service.getId()));
    }

    // Her personel icin o GUNKU meşgul aralıklarını (randevularını) ve
    // efektif çalışma saatlerini toplayıp AvailabilityCalculator.StaffAvailability
    // listesine çevirir. O gün çalışmayan (izinli/kapalı) personel listeye
    // hiç girmez -- resolveStaffHours Optional.empty() dönerse atlanır.
    private List<AvailabilityCalculator.StaffAvailability> buildStaffAvailabilities(
            Business business, Long businessId, LocalDate date, List<Staff> qualifyingStaff) {
        List<AppointmentStatus> blockingStatuses = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1).minusNanos(1);

        List<AvailabilityCalculator.StaffAvailability> result = new ArrayList<>();
        for (Staff staff : qualifyingStaff) {
            resolveStaffHours(staff, business, businessId, date).ifPresent(hours -> {
                List<Appointment> staffAppointments = appointmentRepository
                        .findByStaffIdAndAppointmentDateBetweenAndStatusIn(staff.getId(), dayStart, dayEnd, blockingStatuses);
                List<BusyInterval> busy = staffAppointments.stream()
                        .map(a -> new BusyInterval(a.getAppointmentDate(),
                                a.getAppointmentDate().plusMinutes(a.getServiceItem().getDurationInMinutes())))
                        .toList();
                result.add(new AvailabilityCalculator.StaffAvailability(staff.getId(), hours.openTime(), hours.closeTime(), busy));
            });
        }
        return result;
    }

    // Bir personelin belirli bir gündeki efektif çalışma saatlerini döner.
    // Personelin KENDİ StaffWorkingHour'u varsa o kullanılır; yoksa
    // (henüz saatleri ayrı ayarlanmamış, yeni eklenmiş personel) işletmenin
    // genel saatine düşülür (resolveWorkingHours) -- bu sayede yeni eklenen
    // bir personel, saatleri elle girilmeden hemen rezervasyona açık olur.
    // İşletme çapında özel kapanış (tatil), personelin kendi saati olsa
    // bile HER ZAMAN geçerli -- resolveWorkingHours'ın fallback dalında
    // zaten kontrol ediliyor, personelin kendi saati olduğu daldaysa burada
    // ayrıca kontrol ediliyor.
    private Optional<EffectiveHours> resolveStaffHours(Staff staff, Business business, Long businessId, LocalDate date) {
        Optional<StaffWorkingHour> staffHour = staffWorkingHourRepository
                .findByStaffIdAndDayOfWeek(staff.getId(), date.getDayOfWeek());

        if (staffHour.isEmpty()) {
            return resolveWorkingHours(businessId, business, date);
        }

        if (businessClosureRepository.findByBusinessIdAndDate(businessId, date).isPresent()) {
            return Optional.empty();
        }

        StaffWorkingHour swh = staffHour.get();
        return swh.isClosed()
                ? Optional.empty()
                : Optional.of(new EffectiveHours(swh.getOpenTime(), swh.getCloseTime()));
    }

    // createAppointment'ta musteri hicbir staffId gondermediginde (Faz 2.9
    // itibariyle su anki TEK client davranisi) cagrilir. Musait olan
    // personeller arasindan EN AZ DOLU olani secer -- AvailabilityCalculator.
    // calculateForStaff'taki "fark etmez" atama kuralinin (bkz. o sinif)
    // tek bir randevu icin, grid'e bagli olmadan (herhangi bir tam saat
    // icin dogru calisan) versiyonu. Hicbir personel musait degilse
    // Optional.empty() doner -- cagiran taraf bunu 409'a cevirir.
    private Optional<Staff> autoAssignStaff(List<Staff> qualifyingStaff, Business business, Long businessId,
            LocalDate date, LocalDateTime newStart, LocalDateTime newEnd,
            LocalDateTime dayStart, LocalDateTime dayEnd) {
        List<AppointmentStatus> blockingStatuses = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);

        Staff best = null;
        int bestBusyCount = Integer.MAX_VALUE;

        for (Staff staff : qualifyingStaff) {
            Optional<EffectiveHours> staffHours = resolveStaffHours(staff, business, businessId, date);
            if (staffHours.isEmpty()) {
                continue;
            }
            EffectiveHours h = staffHours.get();
            if (newStart.toLocalTime().isBefore(h.openTime()) || newEnd.toLocalTime().isAfter(h.closeTime())) {
                continue;
            }

            List<Appointment> staffAppointments = appointmentRepository
                    .findByStaffIdAndAppointmentDateBetweenAndStatusIn(staff.getId(), dayStart, dayEnd, blockingStatuses);

            boolean free = staffAppointments.stream().noneMatch(existing -> {
                LocalDateTime existingStart = existing.getAppointmentDate();
                LocalDateTime existingEnd = existingStart.plusMinutes(existing.getServiceItem().getDurationInMinutes());
                return newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart);
            });

            if (free && staffAppointments.size() < bestBusyCount) {
                best = staff;
                bestBusyCount = staffAppointments.size();
            }
        }

        return Optional.ofNullable(best);
    }

    // Verilen tarih icin efektif calisma saatlerini dondurur. Optional.empty()
    // donerse o gun tamamen kapali demektir (ozel kapanis ya da WorkingHour'da
    // isClosed=true). WorkingHour hic girilmemisse Business'in genel
    // saatlerine geriye donuk uyumlu sekilde duser (bkz. WorkingHour.java).
    // Hem getAvailableTimeSlots hem createAppointment AYNI kurali kullanmali
    // — aksi halde musteri /available-slots'ta hic gorunmeyen bir saate,
    // /create'e dogrudan istek atarak randevu alabilirdi.
    private Optional<EffectiveHours> resolveWorkingHours(Long businessId, Business business, LocalDate date) {
        if (businessClosureRepository.findByBusinessIdAndDate(businessId, date).isPresent()) {
            return Optional.empty();
        }

        Optional<WorkingHour> workingHour = workingHourRepository
                .findByBusinessIdAndDayOfWeek(businessId, date.getDayOfWeek());

        if (workingHour.isPresent()) {
            WorkingHour wh = workingHour.get();
            return wh.isClosed()
                    ? Optional.empty()
                    : Optional.of(new EffectiveHours(wh.getOpenTime(), wh.getCloseTime()));
        }

        return Optional.of(new EffectiveHours(business.getOpenTime(), business.getCloseTime()));
    }
}
