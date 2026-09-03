package com.randevu.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Email boş olamaz.")
    @Email(message = "Geçerli bir email adresi girin.")
    private String email;

    // Bilerek @Size yok — login'de sifre POLICY kontrolu yapilmaz, sadece
    // bos olup olmadigina bakilir. Uzunluk kurali yalnizca RegisterRequest'te.
    @NotBlank(message = "Şifre boş olamaz.")
    private String password;
}
