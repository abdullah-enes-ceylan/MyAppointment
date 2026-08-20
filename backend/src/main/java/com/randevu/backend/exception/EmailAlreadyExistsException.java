package com.randevu.backend.exception;

// Kayit sirasinda email zaten kullanimdaysa firlatilir.
// Not: Faz 0.3'te global exception handler kurulunca bu sinif oraya baglanacak;
// simdilik controller kendi yakalayip 409 donuyor (AuthController'daki desenle tutarli).
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("Bu email adresi zaten kayitli: " + email);
    }
}
