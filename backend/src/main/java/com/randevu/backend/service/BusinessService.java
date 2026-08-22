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

    public List<Business> getAllBusinesses() {
        return businessRepository.findAll();
    }

    // Tekil işletme detayı — eskiden bu uç hiç yoktu, frontend tüm listeyi
    // (GET /api/businesses) çekip client tarafında id'ye göre filtreliyordu.
    public Business getBusinessById(Long id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));
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

    // Gelen metni Enum'a çevirir ve filtreler. Geçersiz kategorilerde boş liste
    // döner.
    public List<Business> getBusinessesByCategory(String categoryStr) {
        try {
            BusinessCategory category = BusinessCategory.valueOf(categoryStr.toUpperCase());
            return businessRepository.findByCategory(category);
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }

}
