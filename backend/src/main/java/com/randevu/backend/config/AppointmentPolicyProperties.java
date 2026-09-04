package com.randevu.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Randevu yasam dongusu ayarlari TEK yerde.
//
// Neden dagitik @Value degil: bu dort deger birlikte anlam tasiyor (bekleme
// suresi, oran, ufuk, acik talep siniri) ve birlikte degistiriliyor. Tipli
// tek bir sinifta olunca hem hangi ayarlarin var oldugu kodu okumadan
// gorulebiliyor hem de bu sinifi alan her yer tum politikaya erisiyor.
//
// Bu degerlerin ayardan gelmesi bilincli bir gereksinim: beta'da sahadan
// "24 saat cok, 12 olsun" gibi geri bildirim gelecek ve bunun kod
// degisikligi + derleme + deploy gerektirmemesi gerekiyor.
@Component
@ConfigurationProperties(prefix = "app.appointment")
public class AppointmentPolicyProperties {

    private static final Logger log = LoggerFactory.getLogger(AppointmentPolicyProperties.class);

    private static final Duration DEFAULT_EXPIRY_LEAD_TIME = Duration.ofHours(1);
    private static final double DEFAULT_EXPIRY_RATIO = 0.10;
    private static final Duration DEFAULT_BOOKING_HORIZON = Duration.ofDays(90);
    private static final int DEFAULT_MAX_OPEN_REQUESTS = 3;
    // AvailabilityCalculator'ın izgara adımıyla (app.scheduling.slot-granularity-minutes,
    // varsayılan 15 dk) BİLEREK aynı büyüklükte: "bugün" için müsaitlik
    // listesi artık şu andan itibaren en az bir izgara adımı kadar ileriye
    // bakıyor. Bunun altında bir pay pratikte anlamsız olurdu (kullanıcı
    // saati görüp tıklayana kadar zaten geçer), üstünde bir pay ise
    // gerçekte müsait olan bir saati gereksiz yere gizlerdi.
    private static final Duration DEFAULT_MINIMUM_BOOKING_LEAD_TIME = Duration.ofMinutes(15);

    // Talep, randevu saatinden BU KADAR once cevaplanmamissa duser.
    private Duration expiryLeadTime = DEFAULT_EXPIRY_LEAD_TIME;

    // Randevu cok yakinsa sabit pay pencereden buyuk olur; o durumda
    // pencerenin bu orani kadar pay birakilir. Sabit pay kullanilsaydi
    // 30 dakika sonrasina alinan bir randevu DOGDUGU ANDA suresi dolmus
    // olurdu -- oran bunu engelliyor.
    private double expiryRatio = DEFAULT_EXPIRY_RATIO;

    // En fazla ne kadar ileriye randevu alinabilir. Onceden hicbir ust sinir
    // yoktu (@Future sadece gecmisi engelliyordu), yani 2099'a randevu
    // alinabiliyordu. Sinir olmadan bir talep slotu aylarca kilitleyebilir;
    // ayrica isletme o kadar ileriyi taahhut edemez (fiyat degisir, personel
    // ayrilir, calisma saatleri degisir).
    private Duration bookingHorizon = DEFAULT_BOOKING_HORIZON;

    // Bir musterinin AYNI ISLETMEDE ayni anda kac acik (PENDING) talebi
    // olabilir. Isletme bazinda cunku saldiri senaryosu "bir isletmenin
    // takvimini doldurmak"; genel bir sinir uc farkli isletmeden cevap
    // bekleyen normal kullaniciyi da cezalandirirdi. APPROVED sayilmiyor:
    // duzenli musterinin mevcut randevusu varken bir sonrakini almasi
    // engellenmemeli.
    private int maxOpenRequestsPerBusiness = DEFAULT_MAX_OPEN_REQUESTS;

    // "Bugün" için müsaitlik listesinden, şu andan itibaren bu kadar
    // yakındaki saatler çıkarılır (bkz. AppointmentService.excludePastSlotsForToday).
    // Sıfır olabilir ("sadece gerçekten geçmiş saatleri ele, yakınlık payı
    // isteme") ama negatif olamaz.
    private Duration minimumBookingLeadTime = DEFAULT_MINIMUM_BOOKING_LEAD_TIME;

    // Gecersiz ayarlar uygulamayi durdurmuyor, guvenli varsayilana dusup
    // uyari logluyor -- AvailabilityCalculator.effectiveGranularity ile ayni
    // gerekce: bunlar kullanici girdisi degil ops hatasi, tek bir yanlis
    // satir yuzunden uygulamanin hic acilmamasi ya da tum isteklerin 500'e
    // dusmesi orantisiz olur. Uyari acilista bir kez gorunur.
    @PostConstruct
    public void validate() {
        if (expiryLeadTime == null || expiryLeadTime.isNegative() || expiryLeadTime.isZero()) {
            log.warn("app.appointment.expiry-lead-time geçersiz ({}), varsayılan {} kullanılıyor",
                    expiryLeadTime, DEFAULT_EXPIRY_LEAD_TIME);
            expiryLeadTime = DEFAULT_EXPIRY_LEAD_TIME;
        }
        // Oran (0,1) araliginda olmali: 0 ve altinda pay kalmaz (talep randevu
        // aninda duser), 1 ve ustunde talep dogar dogmaz duser.
        if (expiryRatio <= 0 || expiryRatio >= 1) {
            log.warn("app.appointment.expiry-ratio geçersiz ({}), varsayılan {} kullanılıyor",
                    expiryRatio, DEFAULT_EXPIRY_RATIO);
            expiryRatio = DEFAULT_EXPIRY_RATIO;
        }
        if (bookingHorizon == null || bookingHorizon.isNegative() || bookingHorizon.isZero()) {
            log.warn("app.appointment.booking-horizon geçersiz ({}), varsayılan {} kullanılıyor",
                    bookingHorizon, DEFAULT_BOOKING_HORIZON);
            bookingHorizon = DEFAULT_BOOKING_HORIZON;
        }
        if (maxOpenRequestsPerBusiness < 1) {
            log.warn("app.appointment.max-open-requests-per-business geçersiz ({}), varsayılan {} kullanılıyor",
                    maxOpenRequestsPerBusiness, DEFAULT_MAX_OPEN_REQUESTS);
            maxOpenRequestsPerBusiness = DEFAULT_MAX_OPEN_REQUESTS;
        }
        if (minimumBookingLeadTime == null || minimumBookingLeadTime.isNegative()) {
            log.warn("app.appointment.minimum-booking-lead-time geçersiz ({}), varsayılan {} kullanılıyor",
                    minimumBookingLeadTime, DEFAULT_MINIMUM_BOOKING_LEAD_TIME);
            minimumBookingLeadTime = DEFAULT_MINIMUM_BOOKING_LEAD_TIME;
        }
    }

    public Duration getExpiryLeadTime() {
        return expiryLeadTime;
    }

    public void setExpiryLeadTime(Duration expiryLeadTime) {
        this.expiryLeadTime = expiryLeadTime;
    }

    public double getExpiryRatio() {
        return expiryRatio;
    }

    public void setExpiryRatio(double expiryRatio) {
        this.expiryRatio = expiryRatio;
    }

    public Duration getBookingHorizon() {
        return bookingHorizon;
    }

    public void setBookingHorizon(Duration bookingHorizon) {
        this.bookingHorizon = bookingHorizon;
    }

    public int getMaxOpenRequestsPerBusiness() {
        return maxOpenRequestsPerBusiness;
    }

    public void setMaxOpenRequestsPerBusiness(int maxOpenRequestsPerBusiness) {
        this.maxOpenRequestsPerBusiness = maxOpenRequestsPerBusiness;
    }

    public Duration getMinimumBookingLeadTime() {
        return minimumBookingLeadTime;
    }

    public void setMinimumBookingLeadTime(Duration minimumBookingLeadTime) {
        this.minimumBookingLeadTime = minimumBookingLeadTime;
    }
}
