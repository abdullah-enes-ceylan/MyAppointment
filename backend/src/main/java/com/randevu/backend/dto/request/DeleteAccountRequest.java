package com.randevu.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Hesap silme talebi (Faz 3.9). ChangePasswordRequest'teki ayni "yeniden
// kimlik dogrulama" gerekcesi: sifre yeniden istenir ki ele gecmis/acik
// unutulmus bir oturum tek basina hesabi silme yetkisi kazanmasin.
@Getter
@Setter
public class DeleteAccountRequest {

    @NotBlank(message = "Şifre boş olamaz.")
    private String password;
}
