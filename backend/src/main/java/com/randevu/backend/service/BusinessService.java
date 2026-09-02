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
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class BusinessService {
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final EntityManager entityManager;

    public BusinessService(BusinessRepository businessRepository, UserRepository userRepository,
            ReviewRepository reviewRepository, EntityManager entityManager) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.entityManager = entityManager;
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

    // Kapak fotografi yukleme (BusinessPhotoService) icin: eski photo_key'i
    // okuyup yeni key ile degistirir. Bilerek TEK transaction icinde,
    // PESSIMISTIC_WRITE kilidiyle -- kilitsiz bir oku-yaz olsaydi, ayni
    // isletmeye eszamanli iki yukleme AYNI eski key'i okur, ikisi de kendi
    // yeni dosyasini yazar ama kaybeden istegin dosyalari DB'de hic
    // referanslanmadigi icin sonsuza dek diskte oksuz kalirdi (bkz. plan
    // "Isletme Kapak Fotografi" madde 7 "Eszamanli iki yukleme"). Bu metot
    // BusinessPhotoService'ten (FARKLI bir bean) cagrildigi icin @Transactional
    // proxy'si duzgun devreye giriyor -- BusinessPhotoService icinde ayni
    // sinifin baska bir metodunu "this." ile cagirsaydik proxy atlanir,
    // kilit hic calismazdi (Spring'in bilinen self-invocation tuzagi).
    public record PhotoKeySwapResult(Business business, String previousPhotoKey) {
    }

    @Transactional
    public PhotoKeySwapResult swapPhotoKey(Long businessId, String newPhotoKey) {
        Business business = businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        // KRITIK: spring.jpa.open-in-view=true oldugu icin bu HTTP istegi
        // boyunca TEK bir persistence context (1. seviye onbellek) paylasilir.
        // Bu metottan ONCE ayni istekte OwnershipGuard.assertOwnsBusiness ZATEN
        // bu isletmeyi (KILITSIZ) okumus ve onbellege koymus olabilir. JPA'nin
        // kimlik haritasi kurali geregi, findByIdForUpdate'in SQL'i (kilit
        // dahil) GERCEKTEN calissa bile, persistence context'te ayni id'li bir
        // nesne ZATEN varsa Hibernate o ESKI Java nesnesini dondurur -- yeni
        // sorgunun getirdigi sutun degerlerini YOK SAYAR. refresh() bunu
        // zorla DB'deki GUNCEL (ve artik kilitli/garantili taze) degerlerle
        // degistirir. Bu satir olmadan iki eszamanli fotograf yuklemesi
        // ikisi de "eski" olarak AYNI bayat key'i okur, biri digerinin az once
        // yazdigi dosyalari sonsuza dek oksuz birakir -- bu varsayim degil,
        // canli eszamanli curl testiyle once SOMUT OLARAK GOZLEMLENMIS, sonra
        // bu satirla dogrulanarak kapatilmis bir hata.
        entityManager.refresh(business);

        String previousPhotoKey = business.getPhotoKey();
        business.setPhotoKey(newPhotoKey);
        Business saved = businessRepository.save(business);

        return new PhotoKeySwapResult(saved, previousPhotoKey);
    }

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
