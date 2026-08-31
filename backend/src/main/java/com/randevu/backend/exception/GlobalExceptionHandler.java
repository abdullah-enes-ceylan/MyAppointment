package com.randevu.backend.exception;

import com.randevu.backend.dto.response.ErrorResponse;
import com.randevu.backend.dto.response.ValidationErrorResponse;
import com.randevu.backend.logging.PiiMasker;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
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

    // Hata zaman damgalari da tek saat kaynagindan -- bkz.
    // RestAuthenticationEntryPoint'teki ayni gerekce.
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

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
                clock.instant(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Girdiğiniz bilgilerde hata var.",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Veritabaninin kendi kisitlamasi (unique index, FK vb.) ihlal edildi -> 409.
    // En somut ornek: AppointmentSlotIndexInitializer'daki kismi unique index.
    // Iki musteri ayni saate ES ZAMANLI randevu isteginde bulunursa, uygulama
    // katmanindaki "exists" kontrolu ikisini de gecirebilir (klasik race
    // condition) — ama ikinci save() cagrisi bu index'i ihlal edip
    // DataIntegrityViolationException firlatir. Bu handler olmadan istemci
    // 500 gorurdu; oysa bu aslinda 409'luk, anlamli bir durum ("bu saat az
    // once dolduruldu"). Gercek SQL/kisitlama detayi sadece logda kalir,
    // istemciye asla sizmaz. Mesaj PiiMasker'dan geciriliyor -- ornegin
    // users.email UNIQUE kisitini ihlal eden bir yaris durumunda, Postgres'in
    // urettigi ham "Detail: Key (email)=(x@y.com) already exists." mesaji
    // e-postayi duz metin loglardi (bkz. PiiMasker gerekcesi).
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        log.warn("Veri bütünlüğü ihlali — {} {}: {}", request.getMethod(), request.getRequestURI(),
                PiiMasker.maskEmails(ex.getMostSpecificCause().getMessage()));
        return build(HttpStatus.CONFLICT, "Bu işlem mevcut bir kayıtla çakışıyor.", request);
    }

    // Istenen path'e eslesen HICBIR controller metodu yok -> 404. Iki farkli
    // exception tipi ayni sonuca goturuyor: NoHandlerFoundException hicbir
    // handler eslesmediginde, NoResourceFoundException ise (bu projede
    // GERCEKTE olusan) istek Spring'in varsayilan static resource
    // handler'ina (/** ile eslesen ResourceHttpRequestHandler) dusup dosya
    // olarak da bulunamadiginda firliyor. Bu handler olmadan, silinen/
    // yanlis yazilan bir yola atilan istek catch-all Exception handler'ina
    // dusup 500 donerdi — oysa bu tamamen istemcinin sucu (var olmayan bir
    // kaynagi istedi), sunucunun degil. Faz 1.5'te /api/businesses/owner/{id}
    // kaldirilirken bu fark edildi.
    @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "İstenen adres bulunamadı.", request);
    }

    // Hatali e-posta/sifre -> 401. Eskiden AuthController bunu kendi
    // try/catch'iyle yakalayip govdeye DUZ STRING yaziyordu; API'nin geri
    // kalani ErrorResponse donerken login tek basina farkli bir sekil
    // uretiyordu. Bunun somut bedeli frontend'e yansimisti: LoginPage
    // "bazen duz metin, bazen nesne" diye iki bicimi birden ele almak
    // zorunda kaliyordu.
    //
    // Mesaj BILEREK genel: "bu e-posta kayitli degil" ile "sifre yanlis"i
    // ayirmak, saldirgana hangi e-postalarin sistemde oldugunu tek tek
    // dogrulatir (kullanici numaralandirma). Hangi alanin yanlis oldugunu
    // asla soylemiyoruz.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex,
            HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Hatalı e-posta veya şifre.", request);
    }

    // Faz 3.5: login kaba-kuvvet korumasi ve fotograf yukleme limiti (bkz.
    // AuthController, BusinessController, InMemoryRateLimiter). 429 --
    // istemciye "az sonra tekrar dene" demenin standart yolu. build()
    // KULLANILMIYOR: bu tek istisna "Retry-After" header'i da tasimasi
    // gereken ozel durum, build() sade bir ResponseEntity dondugu icin ek
    // header eklemeye elverisli degil.
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex,
            HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                clock.instant(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    // Istek govdesi hic okunamadi -> 400. Jackson JSON'i parse edemediginde
    // (bozuk sozdizimi, gecersiz UTF-8 byte'i, beklenen tipe uymayan deger,
    // hatta bos govde) Spring bu exception'i firlatir. NoResourceFoundException
    // ile ayni mantik: bu tamamen ISTEMCININ sucu, sunucunun degil — o yuzden
    // 500 degil 400 donmeli. Ayrica 500 donmek hata izlemeyi de kirletir:
    // uzerine aksiyon alinamayacak "sunucu hatasi" alarmlari uretir.
    // Parser'in ham mesajini ISTEMCIYE VERMIYORUZ; icinde govdenin bir parcasi
    // (yani kullanici verisi/PII) ve ic sinif isimleri gecebiliyor. Detay
    // DataIntegrityViolationException'daki gibi sadece warn seviyesinde logda
    // kalir (yine PiiMasker'dan gecirilerek -- Jackson'in mesaji ayristirmaya
    // calistigi ham degeri, ornegin istek govdesindeki e-postayi, icerebiliyor);
    // istemci genel bir mesaj gorur.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Okunamayan istek gövdesi — {} {}: {}", request.getMethod(), request.getRequestURI(),
                PiiMasker.maskEmails(ex.getMostSpecificCause().getMessage()));
        return build(HttpStatus.BAD_REQUEST, "İstek gövdesi okunamadı.", request);
    }

    // Servlet seviyesindeki multipart siniri asildi (spring.servlet.multipart.
    // max-file-size/max-request-size) -> 413. Bu, bizim BusinessPhotoService'in
    // anlamli BusinessRuleException'indan ONCE devreye giriyor cunku Spring bu
    // kontrolu dosya govdesi TAMAMEN okunmadan, cok erken yapiyor -- bkz.
    // BusinessPhotoProperties.maxSizeBytes'in bu sinirdan dusuk tutulmasi
    // gerekce (plan "Isletme Kapak Fotografi" madde 9).
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Yüklenen dosya çok büyük.", request);
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
                clock.instant(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
