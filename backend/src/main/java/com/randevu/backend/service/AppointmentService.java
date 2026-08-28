package com.randevu.backend.service;

import com.randevu.backend.entity.*;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.*;
import com.randevu.backend.service.AvailabilityCalculator.BusyInterval;
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
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository,
            BusinessRepository businessRepository,
            ServiceItemRepository serviceItemRepository,
            WorkingHourRepository workingHourRepository,
            BusinessClosureRepository businessClosureRepository,
            AvailabilityCalculator availabilityCalculator,
            StaffRepository staffRepository,
            StaffWorkingHourRepository staffWorkingHourRepository,
            Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.businessRepository = businessRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.workingHourRepository = workingHourRepository;
        this.businessClosureRepository = businessClosureRepository;
        this.availabilityCalculator = availabilityCalculator;
        this.staffRepository = staffRepository;
        this.staffWorkingHourRepository = staffWorkingHourRepository;
        this.clock = clock;
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
        // Business'ı hiç yüklemiyor. Dolayısıyla işletmeye bağlı herhangi bir
        // kuralı (ör. ileride eklenebilecek "otomatik onay" seçeneği) okuyamazdı.
        // Gerçek Business burada, yukarıda yükleniyor -- karar da buraya ait.
        newAppointment.setStatus(AppointmentStatus.PENDING);

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
                requireCurrentStatus(appointment, EnumSet.of(AppointmentStatus.PENDING),
                        "Sadece onay bekleyen randevular onaylanabilir.");
                appointment.setStatus(AppointmentStatus.APPROVED);
            }
            case REJECT -> {
                requireOwner(isBusinessOwner, "Bu işlemi yalnızca işletme sahibi yapabilir.");
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

    private void requireOwner(boolean isBusinessOwner, String message) {
        if (!isBusinessOwner) {
            throw new AccessDeniedException(message);
        }
    }

    private void requireCurrentStatus(Appointment appointment, Set<AppointmentStatus> allowed, String message) {
        if (!allowed.contains(appointment.getStatus())) {
            throw new BusinessRuleException(message);
        }
    }

    // Müşterinin şu andan sonraki randevularını getirir.
    public List<Appointment> getUpcomingCustomerAppointments(Long customerId) {
        return appointmentRepository.findByCustomerIdAndAppointmentDateAfter(customerId, LocalDateTime.now(clock));
    }

    // İşletmenin şu andan sonraki randevularını getirir.
    public List<Appointment> getUpcomingBusinessAppointments(Long businessId) {
        return appointmentRepository.findByBusinessIdAndAppointmentDateAfter(businessId, LocalDateTime.now(clock));
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
