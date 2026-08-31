package com.randevu.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

// AppointmentPolicyProperties/NotificationProperties ile AYNI desen: sayilar
// koda gomulmez, gecersiz deger uygulamayi durdurmaz, guvenli varsayilana
// dusup uyari loglar. Degerler ilk tahmin -- gercek saha geri bildirimi
// gelirse kod degisikligi/derleme gerektirmeden ayarlanabilir.
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private static final Logger log = LoggerFactory.getLogger(RateLimitProperties.class);

    private static final int DEFAULT_LOGIN_ACCOUNT_MAX_ATTEMPTS = 5;
    private static final Duration DEFAULT_LOGIN_ACCOUNT_WINDOW = Duration.ofMinutes(15);
    private static final int DEFAULT_LOGIN_IP_MAX_ATTEMPTS = 20;
    private static final Duration DEFAULT_LOGIN_IP_WINDOW = Duration.ofMinutes(15);
    private static final int DEFAULT_REGISTER_MAX_REQUESTS = 10;
    private static final Duration DEFAULT_REGISTER_WINDOW = Duration.ofMinutes(15);
    private static final int DEFAULT_AVAILABLE_SLOTS_MAX_REQUESTS = 60;
    private static final Duration DEFAULT_AVAILABLE_SLOTS_WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_PHOTO_UPLOAD_MAX_REQUESTS = 5;
    private static final Duration DEFAULT_PHOTO_UPLOAD_WINDOW = Duration.ofMinutes(5);
    private static final int DEFAULT_GLOBAL_MAX_REQUESTS = 300;
    private static final Duration DEFAULT_GLOBAL_WINDOW = Duration.ofMinutes(1);

    // Login kaba-kuvvet: HESAP ve IP limitleri BILEREK BAGIMSIZ (bkz.
    // AuthController). Sadece hesap limiti olsa saldirgan farkli hesaplari
    // deneyerek kacar (hicbiri tek basina esige ulasmaz); sadece IP limiti
    // olsa dagitik (cok IP'den tek hesaba) saldiri gecer. Ikisi birlikte,
    // biri digerinin kacis yolunu kapatiyor.
    private int loginAccountMaxAttempts = DEFAULT_LOGIN_ACCOUNT_MAX_ATTEMPTS;
    private Duration loginAccountWindow = DEFAULT_LOGIN_ACCOUNT_WINDOW;
    private int loginIpMaxAttempts = DEFAULT_LOGIN_IP_MAX_ATTEMPTS;
    private Duration loginIpWindow = DEFAULT_LOGIN_IP_WINDOW;

    // /api/users/register: kimlik dogrulamasiz (permitAll), sahte hesap
    // uretimine karsi -- e-posta dogrulamasi (ROADMAP 3.10) gelene kadar
    // tek savunma.
    private int registerMaxRequests = DEFAULT_REGISTER_MAX_REQUESTS;
    private Duration registerWindow = DEFAULT_REGISTER_WINDOW;

    // /api/appointments/available-slots: permitAll + her cagrida
    // AvailabilityCalculator hesabi -- ucuz DoS vektoru. Limit yuksek
    // cunku mesru kullanim (birden fazla gun/hizmet denemek) zaten sik
    // cagri uretiyor.
    private int availableSlotsMaxRequests = DEFAULT_AVAILABLE_SLOTS_MAX_REQUESTS;
    private Duration availableSlotsWindow = DEFAULT_AVAILABLE_SLOTS_WINDOW;

    // Isletme kapak fotografi yukleme: KULLANICI id bazinda (IP degil --
    // uc zaten kimlik dogrulamali). Her istekte decode+resize+disk yazma
    // pahali. Isletme bazinda DEGIL kullanici bazinda: saldiri modeli "ele
    // gecirilmis hesap" ya da "kotu niyetli kullanici", ikisi de kullanici
    // kimligine bagli -- isletme bazinda olsaydi 5 isletmesi olan biri 5
    // kat limit kazanirdi.
    private int photoUploadMaxRequests = DEFAULT_PHOTO_UPLOAD_MAX_REQUESTS;
    private Duration photoUploadWindow = DEFAULT_PHOTO_UPLOAD_WINDOW;

    // Tum /api/** icin genel guvenlik agi (IP bazinda, kimlik dogrulanmis
    // istekler DAHIL) -- gozden kacan uclari kapsar. Diger kurallarla
    // CAKISMAZ, UST USTE uygulanir: ayni istek hem kendi ozel kuralina hem
    // bu genel tavana sayilir (katmanli savunma).
    private int globalMaxRequests = DEFAULT_GLOBAL_MAX_REQUESTS;
    private Duration globalWindow = DEFAULT_GLOBAL_WINDOW;

    @PostConstruct
    public void validate() {
        loginAccountMaxAttempts = validatePositiveInt(loginAccountMaxAttempts, DEFAULT_LOGIN_ACCOUNT_MAX_ATTEMPTS,
                "app.rate-limit.login-account-max-attempts");
        loginAccountWindow = validatePositiveDuration(loginAccountWindow, DEFAULT_LOGIN_ACCOUNT_WINDOW,
                "app.rate-limit.login-account-window");
        loginIpMaxAttempts = validatePositiveInt(loginIpMaxAttempts, DEFAULT_LOGIN_IP_MAX_ATTEMPTS,
                "app.rate-limit.login-ip-max-attempts");
        loginIpWindow = validatePositiveDuration(loginIpWindow, DEFAULT_LOGIN_IP_WINDOW,
                "app.rate-limit.login-ip-window");
        registerMaxRequests = validatePositiveInt(registerMaxRequests, DEFAULT_REGISTER_MAX_REQUESTS,
                "app.rate-limit.register-max-requests");
        registerWindow = validatePositiveDuration(registerWindow, DEFAULT_REGISTER_WINDOW,
                "app.rate-limit.register-window");
        availableSlotsMaxRequests = validatePositiveInt(availableSlotsMaxRequests, DEFAULT_AVAILABLE_SLOTS_MAX_REQUESTS,
                "app.rate-limit.available-slots-max-requests");
        availableSlotsWindow = validatePositiveDuration(availableSlotsWindow, DEFAULT_AVAILABLE_SLOTS_WINDOW,
                "app.rate-limit.available-slots-window");
        photoUploadMaxRequests = validatePositiveInt(photoUploadMaxRequests, DEFAULT_PHOTO_UPLOAD_MAX_REQUESTS,
                "app.rate-limit.photo-upload-max-requests");
        photoUploadWindow = validatePositiveDuration(photoUploadWindow, DEFAULT_PHOTO_UPLOAD_WINDOW,
                "app.rate-limit.photo-upload-window");
        globalMaxRequests = validatePositiveInt(globalMaxRequests, DEFAULT_GLOBAL_MAX_REQUESTS,
                "app.rate-limit.global-max-requests");
        globalWindow = validatePositiveDuration(globalWindow, DEFAULT_GLOBAL_WINDOW,
                "app.rate-limit.global-window");
    }

    private int validatePositiveInt(int value, int fallback, String propertyName) {
        if (value < 1) {
            log.warn("{} geçersiz ({}), varsayılan {} kullanılıyor", propertyName, value, fallback);
            return fallback;
        }
        return value;
    }

    private Duration validatePositiveDuration(Duration value, Duration fallback, String propertyName) {
        if (value == null || value.isNegative() || value.isZero()) {
            log.warn("{} geçersiz ({}), varsayılan {} kullanılıyor", propertyName, value, fallback);
            return fallback;
        }
        return value;
    }

    public int getLoginAccountMaxAttempts() {
        return loginAccountMaxAttempts;
    }

    public void setLoginAccountMaxAttempts(int loginAccountMaxAttempts) {
        this.loginAccountMaxAttempts = loginAccountMaxAttempts;
    }

    public Duration getLoginAccountWindow() {
        return loginAccountWindow;
    }

    public void setLoginAccountWindow(Duration loginAccountWindow) {
        this.loginAccountWindow = loginAccountWindow;
    }

    public int getLoginIpMaxAttempts() {
        return loginIpMaxAttempts;
    }

    public void setLoginIpMaxAttempts(int loginIpMaxAttempts) {
        this.loginIpMaxAttempts = loginIpMaxAttempts;
    }

    public Duration getLoginIpWindow() {
        return loginIpWindow;
    }

    public void setLoginIpWindow(Duration loginIpWindow) {
        this.loginIpWindow = loginIpWindow;
    }

    public int getRegisterMaxRequests() {
        return registerMaxRequests;
    }

    public void setRegisterMaxRequests(int registerMaxRequests) {
        this.registerMaxRequests = registerMaxRequests;
    }

    public Duration getRegisterWindow() {
        return registerWindow;
    }

    public void setRegisterWindow(Duration registerWindow) {
        this.registerWindow = registerWindow;
    }

    public int getAvailableSlotsMaxRequests() {
        return availableSlotsMaxRequests;
    }

    public void setAvailableSlotsMaxRequests(int availableSlotsMaxRequests) {
        this.availableSlotsMaxRequests = availableSlotsMaxRequests;
    }

    public Duration getAvailableSlotsWindow() {
        return availableSlotsWindow;
    }

    public void setAvailableSlotsWindow(Duration availableSlotsWindow) {
        this.availableSlotsWindow = availableSlotsWindow;
    }

    public int getPhotoUploadMaxRequests() {
        return photoUploadMaxRequests;
    }

    public void setPhotoUploadMaxRequests(int photoUploadMaxRequests) {
        this.photoUploadMaxRequests = photoUploadMaxRequests;
    }

    public Duration getPhotoUploadWindow() {
        return photoUploadWindow;
    }

    public void setPhotoUploadWindow(Duration photoUploadWindow) {
        this.photoUploadWindow = photoUploadWindow;
    }

    public int getGlobalMaxRequests() {
        return globalMaxRequests;
    }

    public void setGlobalMaxRequests(int globalMaxRequests) {
        this.globalMaxRequests = globalMaxRequests;
    }

    public Duration getGlobalWindow() {
        return globalWindow;
    }

    public void setGlobalWindow(Duration globalWindow) {
        this.globalWindow = globalWindow;
    }
}
