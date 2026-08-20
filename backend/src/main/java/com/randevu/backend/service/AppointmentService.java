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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BusinessRepository businessRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final AvailabilityCalculator availabilityCalculator;

    public AppointmentService(AppointmentRepository appointmentRepository,
            BusinessRepository businessRepository,
            ServiceItemRepository serviceItemRepository,
            AvailabilityCalculator availabilityCalculator) {
        this.appointmentRepository = appointmentRepository;
        this.businessRepository = businessRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.availabilityCalculator = availabilityCalculator;
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

        // Spam tıklama koruması: Aynı dükkan + aynı saat için zaten istek varsa engelle
        List<AppointmentStatus> blockingStatuses = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);
        boolean alreadyExists = appointmentRepository.existsByBusinessIdAndAppointmentDateAndStatusIn(
                businessId,
                newAppointment.getAppointmentDate(),
                blockingStatuses);
        if (alreadyExists) {
            throw new BusinessRuleException("Bu saat için zaten bir randevu isteği mevcut!");
        }

        LocalDateTime newStart = newAppointment.getAppointmentDate();
        LocalDateTime newEnd = newStart.plusMinutes(service.getDurationInMinutes());

        LocalDateTime startOfDay = newStart.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        List<Appointment> dailyAppointments = appointmentRepository
                .findByBusinessIdAndAppointmentDateBetweenAndStatusIn(
                        businessId,
                        startOfDay,
                        endOfDay,
                        blockingStatuses);

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
        APPROVE, REJECT, CANCEL;

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
        }

        return appointmentRepository.save(appointment);
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
        return appointmentRepository.findByCustomerIdAndAppointmentDateAfter(customerId, LocalDateTime.now());
    }

    // İşletmenin şu andan sonraki randevularını getirir.
    public List<Appointment> getUpcomingBusinessAppointments(Long businessId) {
        return appointmentRepository.findByBusinessIdAndAppointmentDateAfter(businessId, LocalDateTime.now());
    }

    // Belirtilen gün için işletmenin ve hizmetin süresine uygun boş saat
    // dilimlerini hesaplar. Bu metodun işi artık sadece veriyi TOPLAMAK
    // (işletme/hizmet var mı, o günün meşgul aralıkları neler) — asıl
    // hesaplama AvailabilityCalculator'a devredildi (bkz. o sınıftaki
    // açıklama: JPA'dan bağımsız, test edilebilir, Faz 2'de personel
    // bazlı hale gelecek algoritma).
    public List<LocalTime> getAvailableTimeSlots(Long businessId, Long serviceId, LocalDate date) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Dükkan bulunamadı."));

        ServiceItem serviceItem = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Hizmet bulunamadı."));

        LocalDateTime startOfDay = date.atTime(business.getOpenTime());
        LocalDateTime endOfDay = date.atTime(business.getCloseTime());

        List<AppointmentStatus> blockingStatuses = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);
        List<Appointment> dailyAppointments = appointmentRepository
                .findByBusinessIdAndAppointmentDateBetweenAndStatusIn(businessId, startOfDay, endOfDay,
                        blockingStatuses);

        List<BusyInterval> busyIntervals = dailyAppointments.stream()
                .map(app -> new BusyInterval(
                        app.getAppointmentDate(),
                        app.getAppointmentDate().plusMinutes(app.getServiceItem().getDurationInMinutes())))
                .toList();

        return availabilityCalculator.calculate(date, business.getOpenTime(), business.getCloseTime(),
                serviceItem.getDurationInMinutes(), busyIntervals);
    }
}
