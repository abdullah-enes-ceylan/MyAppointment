package com.randevu.backend.exception;

import com.randevu.backend.dto.response.ErrorResponse;
import com.randevu.backend.dto.response.ValidationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Butun controller'lardan yakalanmadan kacan exception'lari TEK noktadan
// yakalar ve tutarli bir ErrorResponse govdesine cevirir. @RestControllerAdvice,
// tek tek her controller'a try/catch yazmak yerine bunu global olarak yapar —
// Spring, bir @ExceptionHandler metodunun tipiyle eslesen her exception'i
// otomatik olarak buraya yonlendirir (alt siniflar dahil, en spesifik esleme
// kazanir). Boylece controller'lar hata-govdesi-uretme isinden tamamen
// kurtulur (SRP) ve istemciye asla stack trace ya da ic sistem detayi sizmaz.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Istenen kaynak veritabaninda yok -> 404.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // Istek gecerli ama mevcut durumla celisiyor -> 409.
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // Kullanici dogrulanmis ama bu islemi yapma yetkisi yok -> 403. Su an
    // manuel "if (!isBusinessOwner)" kontrollerinden firliyor; Faz 0.4'te
    // @PreAuthorize eklenince Spring'in kendi ureteceği ayni tip exception'i
    // da bu handler otomatik yakalayacak — kod degismeyecek.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // @Valid basarisiz olunca Spring bu exception'i firlatir. Diger handler'lardan
    // farkli olarak TEK bir mesaj yerine, HANGI alanin NEDEN gecersiz oldugunu
    // (fieldErrors haritasi) da donuyoruz — frontend bu sayede genel bir hata
    // yerine ilgili form alaninin altina spesifik mesaj gosterebilir.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        ValidationErrorResponse body = new ValidationErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Girdiğiniz bilgilerde hata var.",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Son savunma hatti: yukaridaki tiplerin hicbirine uymayan, ongorulmemis
    // her hata buraya duser. Gercek exception sunucu logunda kaydedilir,
    // istemciye ise sadece genel bir mesaj gider — ic detay asla sizmaz.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Beklenmeyen hata — {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen bir hata olustu.", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
