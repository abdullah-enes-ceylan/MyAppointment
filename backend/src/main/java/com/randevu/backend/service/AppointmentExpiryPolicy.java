package com.randevu.backend.service;

import com.randevu.backend.config.AppointmentPolicyProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

// Cevaplanmayan bir randevu talebinin NE ZAMAN dusecegini hesaplar.
//
// KURAL:
//     dusme ani = randevu saati − min(sabitPay, pencere × oran)
//     pencere   = randevu saati − talep ani
//
// Neden iki parcali: sadece sabit pay kullansaydik ("randevudan 1 saat once
// dus"), 30 dakika sonrasina alinan bir randevunun talebi DOGDUGU ANDA
// suresi dolmus olurdu -- kisa vadeli randevu imkansiz hale gelirdi.
// Sadece oran kullansaydik ("pencerenin %90'i") uzun vadede keyfi saatler
// cikardi (5 gun oncesinden alinan randevu icin "14:37'ye kadar cevaplayin")
// ve bunu ne musteriye ne esnafa tek cumleyle anlatabilirdik.
//
// min(...) ikisini tek formulde birlestiriyor: uzun pencerelerde sabit pay,
// kisa pencerelerde oran kendiliginde devreye giriyor. Aralarinda sinir ya
// da sicrama yok.
//
//   5 gun once alindi  -> min(1sa, 12sa)  = 1 saat   -> randevudan 1 sa once
//   1 gun once alindi  -> min(1sa, 2.4sa) = 1 saat   -> 1 saat once
//   6 saat once        -> min(1sa, 36dk)  = 36 dk    -> 36 dakika once
//   30 dakika once     -> min(1sa, 3dk)   = 3 dk     -> 3 dakika once
//
// BU SINIF BILEREK SAAT KAYNAGI TASIMIYOR. Clock inject edilmiyor, "now"
// disaridan parametre geliyor. Sebep: sinifin elinde saat kaynagi olmayinca
// icinde ciplak LocalDateTime.now() cagirma IMKANI kalmiyor -- yani
// TimeConfig'le kurulan tek-saat-kaynagi disiplini burada soz vererek degil
// yapisal olarak korunuyor. Ayni sebeple testte gercek saat teste karisamaz.
@Component
public class AppointmentExpiryPolicy {

    private final AppointmentPolicyProperties properties;

    public AppointmentExpiryPolicy(AppointmentPolicyProperties properties) {
        this.properties = properties;
    }

    // Talebin dusecegi ani hesaplar. Randevu saati talep aninda ya da
    // oncesindeyse (pencere <= 0) talep zaten gecersiz sayilir ve randevu
    // saatinin kendisi dondurulur -- ilk kontrolde derhal duser. Bu durum
    // normalde olusmaz (randevu tarihi @Future ile dogrulaniyor) ama V11'de
    // created_at'i geriye donuk doldurulan eski kayitlar icin gecerli.
    public LocalDateTime expiresAt(LocalDateTime createdAt, LocalDateTime appointmentDate) {
        Duration window = Duration.between(createdAt, appointmentDate);
        if (window.isZero() || window.isNegative()) {
            return appointmentDate;
        }

        Duration ratioBased = Duration.ofSeconds(Math.round(window.toSeconds() * properties.getExpiryRatio()));
        Duration margin = min(properties.getExpiryLeadTime(), ratioBased);
        return appointmentDate.minus(margin);
    }

    // "now" parametre -- bkz. sinif aciklamasindaki gerekce.
    public boolean isExpired(LocalDateTime createdAt, LocalDateTime appointmentDate, LocalDateTime now) {
        return !now.isBefore(expiresAt(createdAt, appointmentDate));
    }

    // Randevu tarihi izin verilen ufkun otesinde mi.
    public boolean isBeyondHorizon(LocalDateTime appointmentDate, LocalDateTime now) {
        return appointmentDate.isAfter(now.plus(properties.getBookingHorizon()));
    }

    public Duration getBookingHorizon() {
        return properties.getBookingHorizon();
    }

    public int getMaxOpenRequestsPerBusiness() {
        return properties.getMaxOpenRequestsPerBusiness();
    }

    public Duration getMinimumBookingLeadTime() {
        return properties.getMinimumBookingLeadTime();
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
