package com.randevu.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Hesap silme akisi ayarlari (Faz 3.9, KVKK unutulma hakki) TEK yerde --
// AppointmentPolicyProperties ile ayni gerekce ve desen.
@Component
@ConfigurationProperties(prefix = "app.account-deletion")
public class AccountDeletionProperties {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionProperties.class);

    private static final Duration DEFAULT_GRACE_PERIOD = Duration.ofDays(30);
    private static final Duration DEFAULT_BUSINESS_NOTICE_PERIOD = Duration.ofHours(72);
    private static final Duration DEFAULT_BUSINESS_REVERSAL_WINDOW = Duration.ofHours(48);

    // Kimlik anonimlestirmesine kadar gecen sure (USER ve BUSINESS_OWNER
    // PAYLASIYOR). 30 gun boyunca hesap TAM OLARAK oldugu gibi kaliyor --
    // ele gecirilip silme tetiklenmisse gercek sahibi bu pencerede geri
    // alabilsin diye. Randevu iptaliyle KARISTIRILMAMALI, o ayri ve kisa.
    private Duration gracePeriod = DEFAULT_GRACE_PERIOD;

    // Haber verme payi: BUSINESS_OWNER silme talep ettigi ANDA, bu sure
    // icindeki randevular hemen iptal edilir. Musteri araciligiyla
    // korunan taraf musteri oldugu icin bu deger musteriye gore, yani
    // KISA olmali -- hicbir musteri randevusuna saatler kala ogrenmemeli.
    private Duration businessNoticePeriod = DEFAULT_BUSINESS_NOTICE_PERIOD;

    // Geri donus penceresi: BUSINESS_OWNER'in silme talebi bu sure icinde
    // geri alinmazsa, isletmelerindeki KALAN TUM randevular topluca iptal
    // edilir. businessNoticePeriod'dan KUCUK VEYA ESIT olmak ZORUNDA --
    // aksi halde iki esik arasina dusen bir randevu hicbir asamada
    // zamaninda yakalanamaz (asagidaki validate() bunu zorluyor).
    private Duration businessReversalWindow = DEFAULT_BUSINESS_REVERSAL_WINDOW;

    // Gecersiz ayarlar uygulamayi durdurmuyor, guvenli varsayilana dusup
    // uyari logluyor -- AvailabilityCalculator.effectiveGranularity ile
    // ayni gerekce.
    @PostConstruct
    public void validate() {
        if (gracePeriod == null || gracePeriod.isNegative() || gracePeriod.isZero()) {
            log.warn("app.account-deletion.grace-period geçersiz ({}), varsayılan {} kullanılıyor",
                    gracePeriod, DEFAULT_GRACE_PERIOD);
            gracePeriod = DEFAULT_GRACE_PERIOD;
        }
        if (businessNoticePeriod == null || businessNoticePeriod.isNegative() || businessNoticePeriod.isZero()) {
            log.warn("app.account-deletion.business-notice-period geçersiz ({}), varsayılan {} kullanılıyor",
                    businessNoticePeriod, DEFAULT_BUSINESS_NOTICE_PERIOD);
            businessNoticePeriod = DEFAULT_BUSINESS_NOTICE_PERIOD;
        }
        if (businessReversalWindow == null || businessReversalWindow.isNegative()
                || businessReversalWindow.isZero()) {
            log.warn("app.account-deletion.business-reversal-window geçersiz ({}), varsayılan {} kullanılıyor",
                    businessReversalWindow, DEFAULT_BUSINESS_REVERSAL_WINDOW);
            businessReversalWindow = DEFAULT_BUSINESS_REVERSAL_WINDOW;
        }
        // Iki deger arasindaki ILISKI de dogrulanmali, tek tek gecerli
        // olmalari yetmiyor -- ROADMAP'teki gerekce: reversal-window
        // notice-period'dan BUYUK olursa, ikisi arasina dusen bir randevu
        // hicbir asamada zamaninda yakalanamaz.
        if (businessReversalWindow.compareTo(businessNoticePeriod) > 0) {
            log.warn(
                    "app.account-deletion.business-reversal-window ({}) business-notice-period'dan ({}) BUYUK olamaz, "
                            + "varsayılanlara ({} / {}) dönülüyor",
                    businessReversalWindow, businessNoticePeriod, DEFAULT_BUSINESS_REVERSAL_WINDOW,
                    DEFAULT_BUSINESS_NOTICE_PERIOD);
            businessReversalWindow = DEFAULT_BUSINESS_REVERSAL_WINDOW;
            businessNoticePeriod = DEFAULT_BUSINESS_NOTICE_PERIOD;
        }
    }

    public Duration getGracePeriod() {
        return gracePeriod;
    }

    public void setGracePeriod(Duration gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    public Duration getBusinessNoticePeriod() {
        return businessNoticePeriod;
    }

    public void setBusinessNoticePeriod(Duration businessNoticePeriod) {
        this.businessNoticePeriod = businessNoticePeriod;
    }

    public Duration getBusinessReversalWindow() {
        return businessReversalWindow;
    }

    public void setBusinessReversalWindow(Duration businessReversalWindow) {
        this.businessReversalWindow = businessReversalWindow;
    }
}
