package com.randevu.backend.service;

import com.randevu.backend.config.BusinessPhotoProperties;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessPhoto;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.BusinessPhotoRepository;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.storage.BusinessPhotoStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Isletme fotograflarinin yasam dongusu: ekle, sil, sirala. Dosya
// dogrulama/yeniden-kodlama BILEREK burada DEGIL, ayri BusinessPhotoImageProcessor'da
// (bkz. o sinifin gerekcesi -- bu sinif buyudukce tek sorumluluk ilkesini
// korumak icin V17'de ayrildi). Bu sinif SADECE "isletmenin fotograf
// listesini nasil degistiririz" sorusuyla ilgileniyor: kilit, limit,
// depolama I/O'sunu tetikleme, DB satiri.
@Service
public class BusinessPhotoService {

    private static final Logger log = LoggerFactory.getLogger(BusinessPhotoService.class);

    private static final String CARD_SUFFIX = "-card.jpg";
    private static final String DETAIL_SUFFIX = "-detail.jpg";

    private final BusinessRepository businessRepository;
    private final BusinessPhotoRepository businessPhotoRepository;
    private final BusinessPhotoStorage photoStorage;
    private final BusinessPhotoImageProcessor imageProcessor;
    private final BusinessPhotoProperties properties;
    private final Clock clock;

    public BusinessPhotoService(BusinessRepository businessRepository,
            BusinessPhotoRepository businessPhotoRepository, BusinessPhotoStorage photoStorage,
            BusinessPhotoImageProcessor imageProcessor, BusinessPhotoProperties properties, Clock clock) {
        this.businessRepository = businessRepository;
        this.businessPhotoRepository = businessPhotoRepository;
        this.photoStorage = photoStorage;
        this.imageProcessor = imageProcessor;
        this.properties = properties;
        this.clock = clock;
    }

    // İsletme satirini kilitliyoruz (findByIdForUpdate, BusinessService'teki
    // eski swapPhotoKey ile AYNI birincil amac: eszamanli iki ekleme ayni
    // "bir sonraki sira numarasi"nı hesaplamasin). ESKİ swapPhotoKey'deki
    // entityManager.refresh() tuzagi BURADA GEREKMIYOR: o, business'in KENDI
    // bir kolonunu (photoKey) guncelleyip persistence context'teki BAYAT
    // kopyayi tazelemek icindi. Burada business'in hicbir alanini
    // degistirmiyoruz -- sadece kilit altinda, businessPhotoRepository
    // uzerinden TAZE (cache'lenmemis) count/max sorgulari atiyoruz, bu
    // yuzden ayni bayatlik riski yok.
    @Transactional
    public List<BusinessPhoto> addPhoto(Long businessId, MultipartFile file) {
        Business business = businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        long currentCount = businessPhotoRepository.countByBusinessId(businessId);
        if (currentCount >= properties.getMaxPhotosPerBusiness()) {
            throw new BusinessRuleException(
                    "En fazla " + properties.getMaxPhotosPerBusiness() + " fotoğraf yükleyebilirsiniz.");
        }

        BusinessPhotoImageProcessor.ProcessedImage processed = imageProcessor.process(file);

        String newKey = UUID.randomUUID().toString();
        writeNewFiles(newKey, processed.cardBytes(), processed.detailBytes());

        // MAX+1, count DEGIL -- silme sonrasi bosluklu siralamada (0, 2, 3)
        // count()==3 ile devam etseydik yeni satir order=3'e carpip VAR OLAN
        // satirla cakisirdi (bkz. BusinessPhotoRepository.findTopBy... yorumu).
        short nextOrder = businessPhotoRepository.findTopByBusinessIdOrderByDisplayOrderDesc(businessId)
                .map(bp -> (short) (bp.getDisplayOrder() + 1))
                .orElse((short) 0);

        BusinessPhoto photo = BusinessPhoto.builder()
                .business(business)
                .photoKey(newKey)
                .displayOrder(nextOrder)
                .createdAt(LocalDateTime.now(clock))
                .build();
        businessPhotoRepository.save(photo);

        return businessPhotoRepository.findByBusinessIdOrderByDisplayOrderAscIdAsc(businessId);
    }

    // Sira BILEREK eski uploadPhoto'daki ile ayni: ONCE DB satiri silinir,
    // SONRA dosyalar. DB silme basarisiz olursa hicbir dosya silinmemis olur,
    // fotograf gostermeye DEVAM eder -- kayip yok. Tersine cevrilseydi bir
    // hatada "dosya yok ama DB'de hala var" tutarsizligina duserdik.
    //
    // photoId'nin GERCEKTEN bu businessId'ye ait oldugu iki kez dogrulaniyor:
    // once OwnershipGuard.assertOwnsActiveBusinessPhoto (cagiran controller'da,
    // fotografin KENDI isletmesi uzerinden), sonra burada
    // findByIdAndBusinessId (savunma amacli ikinci katman) -- IDOR'a karsi
    // (bkz. NOTLAR.md "IDOR" notu).
    @Transactional
    public List<BusinessPhoto> removePhoto(Long businessId, Long photoId) {
        businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        BusinessPhoto photo = businessPhotoRepository.findByIdAndBusinessId(photoId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Fotoğraf bulunamadı."));

        businessPhotoRepository.delete(photo);
        deleteFilesQuietly(photo.getPhotoKey());

        return businessPhotoRepository.findByBusinessIdOrderByDisplayOrderAscIdAsc(businessId);
    }

    // Tekil isletme (detay ucu) icin TAM sirali fotograf listesi -- burada
    // N+1 riski YOK, bu uc zaten tek bir isletmeyi isliyor.
    public List<BusinessPhoto> getPhotos(Long businessId) {
        return businessPhotoRepository.findByBusinessIdOrderByDisplayOrderAscIdAsc(businessId);
    }

    // Liste uclari (ana sayfa, kategori, yakinimdakiler) icin: TUM isletmelerin
    // TUM fotograflari TEK sorguda cekilip business.id'ye gore gruplaniyor
    // (bkz. BusinessPhotoRepository.findByBusinessIdInOrderBy... yorumu) --
    // N isletme icin sorgu sayisi HER ZAMAN 1, satir sayisi degil. Fotografi
    // olmayan bir isletme Map'te hic anahtar olarak GORUNMEZ (bkz.
    // BusinessMapper.toDetailResponse'daki "photos.isEmpty()" kontrolu,
    // cagiran taraf Map.getOrDefault(id, List.of()) kullanmali).
    public Map<Long, List<BusinessPhoto>> getPhotosGroupedByBusinessId(List<Long> businessIds) {
        return businessPhotoRepository.findByBusinessIdInOrderByBusinessIdAscDisplayOrderAscIdAsc(businessIds).stream()
                .collect(Collectors.groupingBy(bp -> bp.getBusiness().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    // Disk/R2 yazma hatasi istemcinin sucu degil, bizim altyapimizin sorunu --
    // BusinessRuleException(409) yerine UncheckedIOException firlatiliyor ki
    // GlobalExceptionHandler'in genel Exception yakalayicisi bunu 500 olarak
    // ele alsin ve ERROR seviyesinde loglasin.
    private void writeNewFiles(String key, byte[] cardBytes, byte[] detailBytes) {
        try {
            photoStorage.store(key + CARD_SUFFIX, cardBytes);
            photoStorage.store(key + DETAIL_SUFFIX, detailBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("İşletme fotoğrafı diske yazılamadı.", e);
        }
    }

    // Silme basarisiz olursa istek yine de BASARILI sayilir -- DB satiri
    // zaten silindi, kullanici acisindan fotograf gitti. Hata sadece WARN
    // olarak loglanir; bkz. eski uploadPhoto'daki ayni gerekce.
    private void deleteFilesQuietly(String key) {
        try {
            photoStorage.delete(key + CARD_SUFFIX);
            photoStorage.delete(key + DETAIL_SUFFIX);
        } catch (IOException e) {
            log.warn("İşletme fotoğrafı silinemedi (key={}): {}", key, e.getMessage());
        }
    }
}
