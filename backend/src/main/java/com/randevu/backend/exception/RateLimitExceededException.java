package com.randevu.backend.exception;

// Faz 3.5: login kaba-kuvvet korumasi ve fotograf yukleme limiti bu
// istisnayi firlatir (bkz. AuthController, BusinessController).
// GlobalExceptionHandler 429'a cevirir VE retryAfterSeconds'i "Retry-After"
// header'ina yaziyor. IP bazli hacim siniri (RateLimitFilter -- /register,
// /available-slots, genel guvenlik agi) bu istisnayi KULLANMIYOR -- o bir
// servlet filtresi, DispatcherServlet'e hic ulasmiyor, GlobalExceptionHandler
// onu yakalayamaz (bkz. RestAuthenticationEntryPoint'teki ayni gerekce);
// filtre kendi JSON govdesini ve header'ini elle yaziyor.
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
