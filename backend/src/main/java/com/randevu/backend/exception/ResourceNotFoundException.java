package com.randevu.backend.exception;

// Istenen kaynak (randevu, isletme, hizmet, kullanici...) veritabaninda
// bulunamadiginda firlatilir. GlobalExceptionHandler bunu 404 Not Found'a cevirir.
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
