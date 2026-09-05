package com.randevu.backend.service;

import com.randevu.backend.dto.request.BusinessRequest;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessCategory;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.mapper.BusinessMapper;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ReviewRepository;
import com.randevu.backend.repository.ReviewStatsProjection;
import com.randevu.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class BusinessService {
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;

    public BusinessService(BusinessRepository businessRepository, UserRepository userRepository,
            ReviewRepository reviewRepository) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
    }

    // Faz 2.7: puan ortalaması + yorum sayısı. ReviewStatsProjection'ı
    // (Spring Data'nın kendi arayüz tipi) doğrudan controller'a sızdırmamak
    // için burada bu küçük, bu sınıfa özel record'a çevriliyor —
    // AppointmentAction'ın AppointmentService içinde public nested enum
    // olması gibi aynı desen.
    public record RatingStats(Double averageRating, long reviewCount) {
    }

    public RatingStats getRatingStats(Long businessId) {
        ReviewStatsProjection stats = reviewRepository.getStatsForBusiness(businessId);
        long count = stats.getReviewCount() != null ? stats.getReviewCount() : 0;
        return new RatingStats(stats.getAverageRating(), count);
    }

    // Askidaki (sahibi hesap silme talep etmis) isletmeler listede GORUNMEZ
    // (Faz 3.9) -- "aramada gorunmuyor" tek basina yetmez demisti, ama bu
    // metot zaten aramanin/listelemenin tek kaynagi.
    public List<Business> getAllBusinesses() {
        return businessRepository.findBySuspendedAtIsNull();
    }

    // Tekil işletme detayı — eskiden bu uç hiç yoktu, frontend tüm listeyi
    // (GET /api/businesses) çekip client tarafında id'ye göre filtreliyordu.
    //
    // Askidaki isletme icin BILEREK 404 (403 DEGIL) -- "yetkin yok" ile
    // "boyle bir isletme yok" arasinda fark belli edilmemeli, aksi halde
    // dogrudan URL ile bir isletmenin silinme surecinde oldugu anlasilirdi
    // (bkz. path traversal/SecurityConfigUnmatchedPathTest'teki ayni desen).
    //
    // viewerUserId null (public/anonim cagiran) icin bu davranis DEGISMEDI.
    // SADECE viewerUserId GERCEKTEN bu isletmenin sahibiyse 404 atlanir --
    // sahip kendi askidaki isletmesini panelinden (InfoTab/LocationTab, ikisi
    // de bu ucu kullaniyor) gormeye devam etmeli, sadece degistirememeli
    // (bkz. OwnershipGuard.assertOwnsActiveBusiness, mutasyon uclarindaki
    // AYRI kontrol). Canlida bulunan gercek bir regresyon: bu overload
    // eklenmeden once sahip kendi paneli "Yukleniyor..."da takilip
    // kaliyordu.
    public Business getBusinessById(Long id) {
        return getBusinessById(id, null);
    }

    public Business getBusinessById(Long id, Long viewerUserId) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));
        boolean isOwnerViewing = viewerUserId != null && business.getOwner().getId().equals(viewerUserId);
        if (business.getSuspendedAt() != null && !isOwnerViewing) {
            throw new ResourceNotFoundException("İşletme bulunamadı.");
        }
        return business;
    }

    public List<Business> getBusinessesByOwner(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Dükkan sahibi bulunamadı"));
        return businessRepository.findByOwnerId(owner.getId());
    }

    // Bu metot iki ayri kayit yapiyor: once kullanicinin rolunu yukseltiyor,
    // sonra isletmeyi kaydediyor. @Transactional olmadan, ikinci save() (ornegin
    // isletme adi/adresi bos oldugu icin) patlarsa, birinci save() ZATEN
    // COMMIT EDILMIS olur — kullanici isletmesi olmadan BUSINESS_OWNER kalir.
    // @Transactional, ikisini TEK bir islem (transaction) olarak sarar: biri
    // basarisiz olursa Spring OTOMATIK ROLLBACK yapar, ikisi de geri alinir.
    @Transactional
    public Business createBusiness(User owner, Business business) {
        // Faz 3.9: hesap silme talep etmiş bir kullanıcı YENİ bir işletme
        // açamaz -- "kapanıyorum" derken aynı anda yeni bir işletme kurmak
        // tutarsız, ayrıca 30 gün içinde talep geri alınmazsa bu yeni
        // işletme de hiç kullanılmadan aynı akışa girerdi.
        if (owner.getDeletionRequestedAt() != null) {
            throw new BusinessRuleException("Hesap silme talebiniz olduğu için yeni işletme oluşturamazsınız.");
        }
        validateBusinessHours(business);
        promoteToBusinessOwnerIfNeeded(owner);
        business.setOwner(owner);
        return businessRepository.save(business);
    }

    // Var olan bir işletmeyi günceller. owner kasıtlı olarak DEĞİŞMİYOR —
    // bir işletmenin sahibi güncelleme isteğiyle değiştirilemez, bu ayrı
    // (ve şu an bu projede olmayan) bir "devretme" işlemi olurdu.
    @Transactional
    public Business updateBusiness(Long businessId, BusinessRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        BusinessMapper.applyToEntity(request, business);
        validateBusinessHours(business);

        return businessRepository.save(business);
    }

    // Açılış saati kapanış saatinden sonra/eşit olursa AvailabilityCalculator
    // sessizce sıfır slot üretir (çökmez, ama işletme sahibi neden hiç
    // randevu alamadığını anlayamaz). Hem create hem update'te kontrol
    // ediliyor ki bu hata en başta, veri kaydedilirken yakalansın.
    private void validateBusinessHours(Business business) {
        if (business.getOpenTime() != null && business.getCloseTime() != null
                && !business.getOpenTime().isBefore(business.getCloseTime())) {
            throw new BusinessRuleException("Açılış saati kapanış saatinden önce olmalıdır.");
        }
    }

    // Eskiden bu satırlar createBusiness'in İÇİNE gömülüydü — "işletme
    // oluştur" diye çağıran bir kod, kullanıcının rolünü de sessizce
    // değiştirdiğini metot imzasından anlayamazdı (gizli yan etki, SRP
    // ihlali). Artık isimli, ayrı bir metot: hem createBusiness'i okuyan
    // kişi ne olduğunu tek bakışta görüyor, hem de bu kural ileride
    // (örn. bir yönetici panelinden manuel rol yükseltme eklenirse) tek
    // başına yeniden kullanılabiliyor.
    private void promoteToBusinessOwnerIfNeeded(User user) {
        if (user.getRole() == Role.USER) {
            user.setRole(Role.BUSINESS_OWNER);
            userRepository.save(user);
        }
    }

    // V17'den itibaren BURADA YOK -- kapak fotografi yukleme/silme artik
    // BusinessPhotoService.addPhoto/removePhoto icinde, businessRepository.
    // findByIdForUpdate'i DOGRUDAN kendi @Transactional metotlarinda
    // kullaniyor (ayri bir bean'e devretmeye gerek kalmadi cunku artik
    // business'in KENDI bir alanini degil, ayri business_photos tablosunu
    // guncelliyoruz -- bkz. BusinessPhotoService'teki entityManager.refresh
    // gerekmedigine dair gerekce).

    // Gelen metni Enum'a çevirir ve filtreler. Geçersiz kategorilerde boş liste
    // döner.
    public List<Business> getBusinessesByCategory(String categoryStr) {
        try {
            BusinessCategory category = BusinessCategory.valueOf(categoryStr.toUpperCase());
            return businessRepository.findByCategoryAndSuspendedAtIsNull(category);
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }

}
