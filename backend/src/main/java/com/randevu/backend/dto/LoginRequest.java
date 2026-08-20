package com.randevu.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email boş olamaz.")
    @Email(message = "Geçerli bir email adresi girin.")
    private String email;

    // Bilerek @Size yok — login'de sifre POLICY kontrolu yapilmaz, sadece
    // bos olup olmadigina bakilir. Uzunluk kurali yalnizca RegisterRequest'te.
    @NotBlank(message = "Şifre boş olamaz.")
    private String password;

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
