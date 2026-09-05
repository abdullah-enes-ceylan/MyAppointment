package com.randevu.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Kayit formundan gelen veriyi tasir. Bilerek "id" ve "role" alani yoktur —
// istemci kendine ID veya rol atayamasin diye (bkz. UserService.registerUser).
// Anotasyonlar sadece "bos olmasin" demiyor — controller'daki @Valid ile
// birlikte calisip, kural ihlal edilirse istek servise hic ulasmadan
// GlobalExceptionHandler'daki MethodArgumentNotValidException handler'ina
// dusuyor ve alan bazli 400 donuyor.
@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Ad boş olamaz.")
    private String name;

    @NotBlank(message = "Soyad boş olamaz.")
    private String surName;

    @NotBlank(message = "Email boş olamaz.")
    @Email(message = "Geçerli bir email adresi girin.")
    private String email;

    // Neden burada min 8, login'de degil: kayit sirasinda YENI bir sifre
    // belirleniyor, kural burada uygulanir. Login sadece "dogru sifre mi"
    // diye bakar — orada uzunluk kontrolu yanlis olurdu (400 yerine 401
    // donmesi gereken bir durumu 400'e cevirirdi).
    @NotBlank(message = "Şifre boş olamaz.")
    @Size(min = 8, message = "Şifre en az 8 karakter olmalı.")
    private String password;

    // Sadece Turkiye mobil formati: "0" + "5" + 9 hane, bosluk/tire YOK --
    // frontend zaten yazarken rakam disini filtreliyor, ama asil sinir burasi
    // (istemci tarafi validasyonu curl ile atlanabilir). Sabit kod/DTO'ya
    // gomulmedi cunku format kendisi (Turkiye mobil numarasi) is kurali degil,
    // basit bir veri sekli dogrulamasi -- AppointmentPolicyProperties'teki
    // "config'den okunan is kurali sayilari" ilkesinin kapsami disinda.
    @NotBlank(message = "Telefon boş olamaz.")
    @Pattern(regexp = "^05\\d{9}$", message = "Telefon numarası 05 ile başlayan 11 haneli olmalı (örn. 05551234567).")
    private String phone;
}
