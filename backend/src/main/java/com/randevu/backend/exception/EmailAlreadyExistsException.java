package com.randevu.backend.exception;

// Kayit sirasinda email zaten kullanimdaysa firlatilir. BusinessRuleException'in
// ozel bir turu oldugu icin GlobalExceptionHandler'da ayrica bir handler
// yazmaya gerek yok — ust sinifin handler'i bunu da yakalar.
public class EmailAlreadyExistsException extends BusinessRuleException {
    public EmailAlreadyExistsException(String email) {
        super("Bu email adresi zaten kayitli: " + email);
    }
}
