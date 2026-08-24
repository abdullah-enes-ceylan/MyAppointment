package com.randevu.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Profil duzenleme formundan gelen veri. RegisterRequest ile ayni
// mass-assignment mantigi: "id", "role" ve "password" alanlari BILEREK
// yok -- kullanici kendi rolunu yukseltemesin, sifreyi de buradan degil
// ayri bir uctan (ChangePasswordRequest) degistirsin.
//
// "email" de BILEREK yok: e-posta hem giris kimligi hem de users
// tablosunda unique. Degistirilebilir yapmak ya cakisma kontrolu (409)
// ya da dogrulama e-postasi akisi gerektirir -- ikisi de su an gereksiz
// karmasiklik. E-posta salt-okunur kalir.
@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "Ad boş olamaz.")
    private String name;

    @NotBlank(message = "Soyad boş olamaz.")
    private String surName;

    @NotBlank(message = "Telefon boş olamaz.")
    private String phone;
}
