package com.randevu.backend.service;

import com.randevu.backend.config.AccountDeletionProperties;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.NotificationType;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.notification.Notification;
import com.randevu.backend.notification.NotificationDeliveryException;
import com.randevu.backend.notification.NotificationService;
import com.randevu.backend.repository.AppointmentRepository;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.FavoriteRepository;
import com.randevu.backend.repository.UserRepository;
import com.randevu.backend.service.AppointmentService.AppointmentAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Hesap silme akisi (Faz 3.9, KVKK unutulma hakki). Detay ve gerekce:
// ROADMAP.md 3.9, AccountDeletionProperties, User.deletionRequestedAt/
// anonymizedAt, Business.suspendedAt.
//
// Bu sinif SADECE "talep anindaki" (senkron) davranisi yonetir: talep
// olusturma, geri alma, BUSINESS_OWNER icin isletmeleri askiya alma ve
// haber verme payi icindeki randevulari hemen iptal etme.
// AccountDeletionScheduler ise ZAMANLA tetiklenen iki adimi (geri donus
// penceresi sonrasi toplu iptal, gracePeriod sonrasi anonimlestirme)
// yonetiyor -- SRP: "ne zaman" ile "talep anında ne olur" ayrimi
// AppointmentService/AppointmentLifecycleScheduler'daki ayni desen.
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final AppointmentRepository appointmentRepository;
    private final FavoriteRepository favoriteRepository;
    private final AppointmentService appointmentService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AccountDeletionProperties properties;

    public AccountDeletionService(UserRepository userRepository, BusinessRepository businessRepository,
            AppointmentRepository appointmentRepository, FavoriteRepository favoriteRepository,
            AppointmentService appointmentService, NotificationService notificationService,
            PasswordEncoder passwordEncoder, Clock clock, AccountDeletionProperties properties) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.appointmentRepository = appointmentRepository;
        this.favoriteRepository = favoriteRepository;
        this.appointmentService = appointmentService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.properties = properties;
    }

    // Silme talebini baslatir. Sifre yeniden istenir (ChangePasswordRequest'teki
    // ayni "yeniden kimlik dogrulama" gerekcesi -- ele gecmis/acik unutulmus
    // bir oturum tek basina hesabi silme yetkisi kazanmamali).
    //
    // USER icin BU KADAR: deletionRequestedAt disinda HICBIR SEY degismiyor --
    // giris, randevular, favoriler 30 gun boyunca AYNEN kaliyor (bkz. User.java
    // ustundeki gerekce, ROADMAP 3.9'daki "geri donus" tartismasi).
    //
    // BUSINESS_OWNER icin ek olarak: sahip oldugu tum isletmeler ANINDA
    // askiya alinir (yeni randevu kabul edilmez, aramada gorunmez) ve
    // businessNoticePeriod icindeki randevular HEMEN iptal edilir + musteriye
    // bildirim gider -- hicbir musteri randevusuna saatler kala ogrenmemeli.
    @Transactional
    public void requestDeletion(User user, String rawPassword) {
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessRuleException("Yönetici hesapları bu yolla silinemez.");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessRuleException("Şifre hatalı.");
        }
        if (user.getDeletionRequestedAt() != null) {
            throw new BusinessRuleException("Hesap silme talebiniz zaten mevcut.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        user.setDeletionRequestedAt(now);
        userRepository.save(user);

        if (user.getRole() == Role.BUSINESS_OWNER) {
            suspendBusinessesAndCancelImminentAppointments(user, now);
        }
    }

    private void suspendBusinessesAndCancelImminentAppointments(User owner, LocalDateTime now) {
        List<Business> businesses = businessRepository.findByOwnerId(owner.getId());
        if (businesses.isEmpty()) {
            return;
        }

        businesses.forEach(b -> b.setSuspendedAt(now));
        businessRepository.saveAll(businesses);

        List<Long> businessIds = businesses.stream().map(Business::getId).toList();
        LocalDateTime noticeThreshold = now.plus(properties.getBusinessNoticePeriod());
        List<AppointmentStatus> nonTerminal = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);

        List<Appointment> imminent = appointmentRepository
                .findByBusinessIdInAndStatusInAndAppointmentDateBefore(businessIds, nonTerminal, noticeThreshold);

        cancelAndNotify(imminent, owner.getId());
    }

    // Talebi geri alir. anonymizedAt DOLUYSA artik geri donus yok -- ama bu
    // durumda zaten giris yapilamiyor (CustomUserDetailsService'teki
    // enabled=false), yani bu satira normal akista hic ulasilmaz; yine de
    // acik ve anlamli bir hata birakiyoruz.
    @Transactional
    public void cancelDeletion(User user) {
        if (user.getAnonymizedAt() != null) {
            throw new BusinessRuleException("Bu hesap zaten anonimleştirildi, geri alınamaz.");
        }
        if (user.getDeletionRequestedAt() == null) {
            throw new BusinessRuleException("Aktif bir silme talebiniz yok.");
        }

        user.setDeletionRequestedAt(null);
        userRepository.save(user);

        if (user.getRole() == Role.BUSINESS_OWNER) {
            List<Business> businesses = businessRepository.findByOwnerId(user.getId());
            businesses.forEach(b -> b.setSuspendedAt(null));
            businessRepository.saveAll(businesses);
        }
    }

    // Frontend'in banner'ında gösterdiği iki tarih. HAM deletionRequestedAt'i
    // donup 30 gun/48 saat gibi sayilari frontend'de tekrar uretmek yerine
    // (config degisirse frontend'i de guncellemek gerekirdi) hazir tarihleri
    // buradan veriyoruz -- AppointmentResponse.expiresAt'teki "kural sunucuda
    // hesaplanir" gerekcesiyle ayni.
    public record DeletionDeadlines(LocalDateTime identityAnonymizationDeadlineAt,
            LocalDateTime businessReversalDeadlineAt) {
    }

    public DeletionDeadlines computeDeadlines(User user) {
        if (user.getDeletionRequestedAt() == null) {
            return new DeletionDeadlines(null, null);
        }
        LocalDateTime identityDeadline = user.getDeletionRequestedAt().plus(properties.getGracePeriod());
        LocalDateTime reversalDeadline = user.getRole() == Role.BUSINESS_OWNER
                ? user.getDeletionRequestedAt().plus(properties.getBusinessReversalWindow())
                : null;
        return new DeletionDeadlines(identityDeadline, reversalDeadline);
    }

    // Silme talebi ONAYLANMADAN ONCE frontend'in gosterecegi uyari icin:
    // "N randevunuz iptal edilecek". USER icin anlamsiz (kendi randevulari
    // ANINDA degil, sadece gracePeriod sonunda etkilenir, bkz. anonymize) --
    // 0 doner. BUSINESS_OWNER icin, su an suspendedAt'ten BAGIMSIZ olarak
    // (talep henuz verilmemis olabilir, bu YUZDEN "onizleme") isletmelerindeki
    // TUM bitmemis (PENDING/APPROVED) randevu sayisi -- bunlarin HEPSI, talep
    // verilirse er ya da gec (hemen ya da 48 saat sonra) iptal olacak.
    public int previewAffectedAppointmentCount(User user) {
        if (user.getRole() != Role.BUSINESS_OWNER) {
            return 0;
        }
        List<Business> businesses = businessRepository.findByOwnerId(user.getId());
        if (businesses.isEmpty()) {
            return 0;
        }
        List<Long> businessIds = businesses.stream().map(Business::getId).toList();
        List<AppointmentStatus> nonTerminal = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);
        return appointmentRepository.findByBusinessIdInAndStatusIn(businessIds, nonTerminal).size();
    }

    // businessReversalWindow gecmis, talep hala aktif bir BUSINESS_OWNER'in
    // kalan TUM randevularini iptal eder. Sorgu SADECE PENDING/APPROVED
    // getirdigi icin idempotent -- AccountDeletionScheduler'in bir sonraki
    // calismasinda ayni isletmeler icin artik iptal edilecek bir sey kalmaz
    // (AppointmentLifecycleScheduler'daki ayni "sorgu kendi idempotentligini
    // sagliyor" deseni).
    @Transactional
    public int bulkCancelRemainingAppointments(User owner) {
        List<Business> businesses = businessRepository.findByOwnerId(owner.getId());
        if (businesses.isEmpty()) {
            return 0;
        }
        List<Long> businessIds = businesses.stream().map(Business::getId).toList();
        List<AppointmentStatus> nonTerminal = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);
        List<Appointment> remaining = appointmentRepository.findByBusinessIdInAndStatusIn(businessIds, nonTerminal);
        cancelAndNotify(remaining, owner.getId());
        return remaining.size();
    }

    // gracePeriod dolmus bir kullanicinin kimligini anonimlestirir: kendi
    // (musteri tarafi) cevaplanmamis/onaylanmis randevularini iptal eder,
    // favorilerini HARD DELETE eder, ad/soyad/e-posta/telefon/sifresini
    // GERI DONDURULEMEZ sekilde degistirir. Randevu/Review/NotificationLog
    // satirlarina HIC dokunulmuyor (bkz. ROADMAP 3.9 -- ReviewMapper zaten
    // musterinin GUNCEL adini canli okuyor, bu satirlar otomatik dogru
    // gorunmeye devam eder).
    @Transactional
    public void anonymize(User user) {
        if (user.getAnonymizedAt() != null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);

        List<AppointmentStatus> nonTerminal = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);
        List<Appointment> ownAppointments = appointmentRepository.findByCustomerIdAndStatusIn(user.getId(), nonTerminal);
        for (Appointment appointment : ownAppointments) {
            try {
                appointmentService.changeStatus(appointment.getId(), AppointmentAction.CANCEL, user.getId());
            } catch (BusinessRuleException e) {
                // Randevu bu arada (ornegin scheduler'in diger tikinde) zaten
                // baska bir terminal duruma gecmis olabilir -- anonimlestirmeyi
                // durdurmaya deger bir sebep degil.
                log.debug("Anonimlestirme sirasinda randevu {} iptal edilemedi: {}", appointment.getId(),
                        e.getMessage());
            }
        }

        favoriteRepository.deleteByUser_Id(user.getId());

        user.setName("Silinmiş Kullanıcı");
        user.setSurName("");
        user.setEmail("deleted-user-" + user.getId() + "@deleted.local");
        user.setPhone("");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setAnonymizedAt(now);
        userRepository.save(user);
    }

    // Bir randevu listesini iptal edip musteriye APPOINTMENT_CANCELLED_BUSINESS_CLOSED
    // bildirimi gonderir. AppointmentNotificationListener'daki ayni hata
    // ayrimi: partial unique index (DataIntegrityViolationException) zaten
    // gonderilmis demektir, gercek gonderim hatasi (NotificationDeliveryException)
    // FAILED olarak loglanir -- ikisi de bu randevunun iptalini GERI ALMAZ.
    private void cancelAndNotify(List<Appointment> appointments, Long actingUserId) {
        for (Appointment appointment : appointments) {
            try {
                appointmentService.changeStatus(appointment.getId(), AppointmentAction.CANCEL, actingUserId);
            } catch (BusinessRuleException e) {
                log.debug("Hesap silme akisinda randevu {} iptal edilemedi: {}", appointment.getId(),
                        e.getMessage());
                continue;
            }

            Notification notification = new Notification(
                    appointment.getCustomer().getId(),
                    NotificationType.APPOINTMENT_CANCELLED_BUSINESS_CLOSED,
                    "Randevunuz iptal edildi",
                    "Gittiğiniz işletme artık hizmet vermiyor, randevunuz iptal edildi.");
            try {
                notificationService.sendAndLog(notification, appointment.getId());
            } catch (DataIntegrityViolationException e) {
                log.debug("APPOINTMENT_CANCELLED_BUSINESS_CLOSED zaten gönderilmiş (randevu {})",
                        appointment.getId());
            } catch (NotificationDeliveryException e) {
                log.warn("APPOINTMENT_CANCELLED_BUSINESS_CLOSED bildirimi gönderilemedi (randevu {}): {}",
                        appointment.getId(), e.getMessage());
                notificationService.logFailure(appointment.getId(), NotificationType.APPOINTMENT_CANCELLED_BUSINESS_CLOSED,
                        appointment.getCustomer().getId(), e.getMessage());
            }
        }
    }
}
