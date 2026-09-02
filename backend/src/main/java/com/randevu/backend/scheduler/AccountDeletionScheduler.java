package com.randevu.backend.scheduler;

import com.randevu.backend.config.AccountDeletionProperties;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.UserRepository;
import com.randevu.backend.service.AccountDeletionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// Hesap silme akisinin ZAMANLA tetiklenen iki adimi (Faz 3.9). Talep
// aninda ne olacagi AccountDeletionService.requestDeletion'da -- bu sinif
// SADECE "ne zaman" sorusunu cevaplıyor, AppointmentLifecycleScheduler ile
// ayni SRP ayrimi.
//
// Ayni cron ifadesini (app.appointment.scheduler-cron) paylasiyor -- ayri
// bir "hesap silme" araligi icin yeni bir config eklemek gereksiz karmasiklik
// olurdu, granulerlik gerekcesi ayni (bkz. AppointmentLifecycleScheduler):
// 5 dakikalik gecikme, gunler/saatler mertebesindeki bu esikler icin
// oransal olarak onemsiz.
@Component
public class AccountDeletionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionScheduler.class);

    private static final String SCHEDULER_CRON = "${app.appointment.scheduler-cron:0 */5 * * * *}";

    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;
    private final Clock clock;
    private final AccountDeletionProperties properties;

    public AccountDeletionScheduler(UserRepository userRepository, AccountDeletionService accountDeletionService,
            Clock clock, AccountDeletionProperties properties) {
        this.userRepository = userRepository;
        this.accountDeletionService = accountDeletionService;
        this.clock = clock;
        this.properties = properties;
    }

    // Geri donus penceresi (businessReversalWindow) dolmus ama talep hala
    // aktif olan BUSINESS_OWNER'larin isletmelerindeki KALAN TUM randevulari
    // topluca iptal eder. Her sahip AYRI bir transaction'da islenir
    // (AccountDeletionService.bulkCancelRemainingAppointments) -- biri
    // basarisiz olursa digerlerini etkilemesin diye.
    @Scheduled(cron = SCHEDULER_CRON)
    public void bulkCancelAfterReversalWindow() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(properties.getBusinessReversalWindow());
        List<User> owners = userRepository
                .findByRoleAndDeletionRequestedAtIsNotNullAndAnonymizedAtIsNullAndDeletionRequestedAtLessThanEqual(
                        Role.BUSINESS_OWNER, cutoff);

        for (User owner : owners) {
            try {
                int cancelled = accountDeletionService.bulkCancelRemainingAppointments(owner);
                if (cancelled > 0) {
                    log.info("Hesap silme (geri dönüş penceresi doldu): işletme sahibi {} için {} randevu iptal edildi.",
                            owner.getId(), cancelled);
                }
            } catch (RuntimeException e) {
                log.error("Hesap silme (geri dönüş penceresi) işlenirken hata — kullanıcı {}", owner.getId(), e);
            }
        }
    }

    // gracePeriod dolmus, HENUZ anonimlestirilmemis her kullaniciyi (USER ve
    // BUSINESS_OWNER paylasiyor) anonimlestirir. Her kullanici AYRI bir
    // transaction'da islenir.
    @Scheduled(cron = SCHEDULER_CRON)
    public void anonymizeAfterGracePeriod() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(properties.getGracePeriod());
        List<User> users = userRepository
                .findByDeletionRequestedAtIsNotNullAndAnonymizedAtIsNullAndDeletionRequestedAtLessThanEqual(cutoff);

        for (User user : users) {
            try {
                accountDeletionService.anonymize(user);
                log.info("Hesap silme (gracePeriod doldu): kullanıcı {} anonimleştirildi.", user.getId());
            } catch (RuntimeException e) {
                log.error("Hesap anonimleştirme işlenirken hata — kullanıcı {}", user.getId(), e);
            }
        }
    }
}
