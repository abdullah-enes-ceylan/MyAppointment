package com.randevu.backend.controller;

import com.randevu.backend.config.RateLimitProperties;
import com.randevu.backend.dto.request.BusinessRequest;
import com.randevu.backend.dto.response.BusinessDetailResponse;
import com.randevu.backend.dto.response.BusinessResponse;
import com.randevu.backend.dto.response.NearbyBusinessResponse;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.RateLimitExceededException;
import com.randevu.backend.mapper.BusinessMapper;
import com.randevu.backend.ratelimit.RateLimitPort;
import com.randevu.backend.ratelimit.RateLimitResult;
import com.randevu.backend.service.BusinessPhotoService;
import com.randevu.backend.service.BusinessService;
import com.randevu.backend.service.BusinessService.RatingStats;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.LocationService;
import com.randevu.backend.service.LocationService.NearbyBusiness;
import com.randevu.backend.service.OwnershipGuard;
import com.randevu.backend.storage.BusinessPhotoStorage;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

    private final BusinessService businessService;
    private final CurrentUserService currentUserService;
    private final OwnershipGuard ownershipGuard;
    private final LocationService locationService;
    private final BusinessPhotoStorage photoStorage;
    private final BusinessPhotoService businessPhotoService;
    private final RateLimitPort rateLimitPort;
    private final RateLimitProperties rateLimitProperties;

    public BusinessController(BusinessService businessService, CurrentUserService currentUserService,
                               OwnershipGuard ownershipGuard, LocationService locationService,
                               BusinessPhotoStorage photoStorage, BusinessPhotoService businessPhotoService,
                               RateLimitPort rateLimitPort, RateLimitProperties rateLimitProperties) {
        this.businessService = businessService;
        this.currentUserService = currentUserService;
        this.ownershipGuard = ownershipGuard;
        this.locationService = locationService;
        this.photoStorage = photoStorage;
        this.businessPhotoService = businessPhotoService;
        this.rateLimitPort = rateLimitPort;
        this.rateLimitProperties = rateLimitProperties;
    }

    // BusinessDetailResponse dönüyor (hizmetler gömülü) — frontend şu an
    // tüm listeyi buradan çekip client'ta filtreliyor ve serviceItems'a
    // ihtiyaç duyuyor. Eskiden entity dönüyordu; herkese açık (permitAll)
    // bu uçta her işletme sahibinin email/telefon/rolü çıplak sızıyordu.
    @GetMapping
    public List<BusinessDetailResponse> getAllBusinesses() {
        return businessService.getAllBusinesses().stream()
                .map(this::toDetailResponseWithRating)
                .toList();
    }

    // YENİ: tekil işletme detayı. Eskiden bu uç hiç yoktu — frontend
    // BusinessDetailPage.jsx tüm listeyi çekip client'ta id'ye göre
    // filtreliyordu, 14 işletmede sorun değil ama 500 işletmede felaket
    // olurdu. {id:\d+} ile SADECE sayısal path'lere eşleşiyor — bu sayede
    // hem SecurityConfig'teki permitAll kuralı hem MVC yönlendirmesi
    // /my gibi başka literal path'lerle asla çakışmıyor.
    // Herkese açık: müsaitlik saatlerinde olduğu gibi, randevu almadan
    // önce müşterinin işletme detayını görebilmesi gerekiyor.
    //
    // (Faz 3.9) Askıdaki bir işletmede bu uç PUBLIC ziyaretçiye 404 döner,
    // ama SAHİBİNE değil -- canlı testte bulunan gerçek bir regresyon:
    // InfoTab/LocationTab (panel) da bu AYNI ucu kullanıyor, "askıdaki
    // işletme sahibi kendi verisini görmeye devam etmeli" ilkesi ihlal
    // ediliyordu (sahip kendi panelinde "Yükleniyor..."da sonsuza takılıp
    // kalıyordu, 404 sessizce hiçbir şey göstermiyordu). authentication
    // BİLEREK nullable/anonim olabilir (uç permitAll) -- sadece GERÇEKTEN
    // giriş yapmış VE bu işletmenin sahibi olan biri suspended kontrolünü
    // atlıyor, herkes için davranış aynı kalıyor.
    @GetMapping("/{id:\\d+}")
    public BusinessDetailResponse getBusinessById(@PathVariable Long id, Authentication authentication) {
        return toDetailResponseWithRating(businessService.getBusinessById(id, resolveViewerIdOrNull(authentication)));
    }

    private Long resolveViewerIdOrNull(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return currentUserService.getCurrentUser(authentication).getId();
        } catch (com.randevu.backend.exception.ResourceNotFoundException e) {
            return null;
        }
    }

    // YENİ: kendi işletmelerim. Eskiden /owner/{ownerId} idi — path'teki
    // ID'nin isteği atanın KENDİ id'siyle eşleşmesi zaten zorunluydu (Faz
    // 0.4), yani parametre fiilen gereksizdi. /appointments/me ile aynı
    // desene taşındı: kimlik daima token'dan gelir, path'te taşınmaz.
    // Frontend bu eski uca hiç bağlı değildi (grep ile doğrulandı).
    @GetMapping("/my")
    public List<BusinessResponse> getMyBusinesses(Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        return businessService.getBusinessesByOwner(currentUser.getId()).stream()
                .map(this::toResponseWithRating)
                .toList();
    }

    // İşletme oluşturma — Token'dan sahip kimliği alınır. Artık Business
    // entity'si değil BusinessRequest DTO alıyor — istemci "owner" veya
    // "id" gönderemez (mass assignment kapalı, bkz. o DTO'daki açıklama).
    @PostMapping("/create")
    public ResponseEntity<BusinessResponse> createBusiness(@Valid @RequestBody BusinessRequest request,
                                            Authentication authentication) {
        User owner = currentUserService.getCurrentUser(authentication);
        Business created = businessService.createBusiness(owner, BusinessMapper.toEntity(request));
        return ResponseEntity.ok(toResponseWithRating(created));
    }

    // YENİ: işletme güncelleme, sahiplik kontrollü. OwnershipGuard olmadan,
    // giriş yapmış herhangi bir kullanıcı başka bir işletmenin bilgilerini
    // (adres, saat, fiyat aralığı vb.) değiştirebilirdi.
    @PutMapping("/{id:\\d+}")
    public BusinessResponse updateBusiness(@PathVariable("id") Long businessId,
                                            @Valid @RequestBody BusinessRequest request,
                                            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsActiveBusiness(currentUser.getId(), businessId);
        Business updated = businessService.updateBusiness(businessId, request);
        return toResponseWithRating(updated);
    }

    // YENİ: kapak fotoğrafı yükleme, sahiplik kontrollü. OwnershipGuard
    // olmadan giriş yapmış herhangi bir kullanıcı başka bir işletmenin
    // kapağını değiştirebilirdi -- updateBusiness ile aynı desen. Dosyanın
    // doğrulanması/yeniden kodlanması/depolanması BusinessPhotoService'te
    // (bkz. o sınıf, plan "Isletme Kapak Fotografi" PR3).
    //
    // Faz 3.5: rate limit KULLANICI id bazinda (IP degil) -- uc zaten
    // kimlik dogrulamali, saldiri modeli "ele gecirilmis/kotu niyetli
    // hesap". OwnershipGuard'DAN SONRA kontrol ediliyor: sahibi olmayan
    // biri zaten 403 aliyor, pahali decode/resize islemine hic girmeden --
    // rate limit sadece GERCEKTEN o isletmenin sahibi olan (dolayisiyla
    // pahali islemi tetikleyebilecek) istekleri sayar.
    @PostMapping("/{id:\\d+}/photo")
    public BusinessResponse uploadPhoto(@PathVariable("id") Long businessId,
                                         @RequestParam("file") MultipartFile file,
                                         Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsActiveBusiness(currentUser.getId(), businessId);

        RateLimitResult result = rateLimitPort.tryConsume("photo-upload:user:" + currentUser.getId(),
                rateLimitProperties.getPhotoUploadMaxRequests(), rateLimitProperties.getPhotoUploadWindow());
        if (!result.allowed()) {
            throw new RateLimitExceededException(
                    "Çok sık fotoğraf yükleme denemesi yapıldı. Lütfen bir süre sonra tekrar deneyin.",
                    result.retryAfterSeconds());
        }

        Business updated = businessPhotoService.uploadPhoto(businessId, file);
        return toResponseWithRating(updated);
    }

    // YENİ: kapak fotoğrafını kaldırma, sahiplik kontrollü -- uploadPhoto ile
    // aynı OwnershipGuard deseni. IDEMPOTENT: zaten fotoğrafı olmayan bir
    // işletmede çağrılırsa da 204 döner, hata fırlatmaz (bkz.
    // BusinessPhotoService.removePhoto) -- FavoriteController.removeFavorite
    // ile aynı "toggle'in kapa ucu" felsefesi.
    @DeleteMapping("/{id:\\d+}/photo")
    public ResponseEntity<Void> removePhoto(@PathVariable("id") Long businessId, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsActiveBusiness(currentUser.getId(), businessId);
        businessPhotoService.removePhoto(businessId);
        return ResponseEntity.noContent().build();
    }

    // Faz 2.8: konuma göre yakın işletme listeleme. /api/businesses ile
    // aynı sebeple herkese açık (permitAll, bkz. SecurityConfig) — müşteri
    // "yakınımdakiler" özelliğini kullanmak için giriş yapmış olmak
    // zorunda değil. lat/lng zorunlu (tarayıcının Geolocation API'sinden
    // ya da manuel şehir seçiminden gelir, bkz. Faz 2.11); radiusKm ve
    // sayfalama parametreleri makul varsayılanlarla opsiyonel.
    @GetMapping("/nearby")
    public List<NearbyBusinessResponse> getNearbyBusinesses(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") double radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<NearbyBusiness> nearby = locationService.findNearby(lat, lng, radiusKm);

        int fromIndex = Math.min(page * size, nearby.size());
        int toIndex = Math.min(fromIndex + size, nearby.size());

        return nearby.subList(fromIndex, toIndex).stream()
                .map(nb -> new NearbyBusinessResponse(toResponseWithRating(nb.business()), nb.distanceKm()))
                .toList();
    }

    // Belirtilen kategori adına göre işletmeleri getirir.
    @GetMapping("/category/{categoryName}")
    public ResponseEntity<List<BusinessDetailResponse>> getBusinessesByCategory(@PathVariable String categoryName) {
        List<BusinessDetailResponse> businesses = businessService.getBusinessesByCategory(categoryName).stream()
                .map(this::toDetailResponseWithRating)
                .toList();
        return ResponseEntity.ok(businesses);
    }

    // Faz 2.7: her işletme yanıtına puan ortalaması + yorum sayısı ekliyor.
    // İş listesi başına bir sorgu (N+1) — bilerek: 5-10 işletmelik beta
    // ölçeğinde önemsiz, erken optimizasyon yapmıyoruz (bkz. ROADMAP 2.7).
    private BusinessResponse toResponseWithRating(Business business) {
        RatingStats stats = businessService.getRatingStats(business.getId());
        return BusinessMapper.toResponse(business, stats.averageRating(), stats.reviewCount(), photoStorage);
    }

    private BusinessDetailResponse toDetailResponseWithRating(Business business) {
        RatingStats stats = businessService.getRatingStats(business.getId());
        return BusinessMapper.toDetailResponse(business, stats.averageRating(), stats.reviewCount(), photoStorage);
    }

}
