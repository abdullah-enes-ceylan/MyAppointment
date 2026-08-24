package com.randevu.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// Sifre degistirme, profil guncellemeden AYRI bir uc -- cunku sadece yeni
// sifreyi almak yetmez, MEVCUT sifre de istenip dogrulanmali. Aksi halde
// ele gecmis ya da acik unutulmus bir oturum (token hala gecerliyken) tek
// basina sifre degistirme yetkisi kazanirdi; mevcut sifreyi sormak buna
// karsi bir "yeniden kimlik dogrulama" katmani.
@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Mevcut şifre boş olamaz.")
    private String currentPassword;

    // RegisterRequest'teki ayni kural: YENI bir sifre belirleniyor, uzunluk
    // kurali burada uygulanir. currentPassword'da @Size YOK -- o sadece
    // "dogru mu" diye kontrol edilir, kurala uymayan eski bir sifre bile
    // olsa dogru girildiginde kabul edilmeli.
    @NotBlank(message = "Yeni şifre boş olamaz.")
    @Size(min = 8, message = "Yeni şifre en az 8 karakter olmalı.")
    private String newPassword;
}
