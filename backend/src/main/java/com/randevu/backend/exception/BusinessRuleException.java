package com.randevu.backend.exception;

// Istek teknik olarak gecerli ama mevcut is kuraliyla/durumla celisiyorsa
// firlatilir (ornek: ayni saate ikinci randevu talebi, zaten kayitli email).
// GlobalExceptionHandler bunu 409 Conflict'e cevirir.
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
