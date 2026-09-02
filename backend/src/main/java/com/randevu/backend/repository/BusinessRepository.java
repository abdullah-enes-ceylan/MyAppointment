package com.randevu.backend.repository;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessCategory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    // işletmecinin iş yerlerini getir. BILEREK suspendedAt'e gore FILTRELENMEZ
    // -- hesap silme talep etmis bir sahip kendi "/my" panelinden askidaki
    // isletmesini gormeye devam etmeli (Faz 3.9, ROADMAP 3.9), aksi halde
    // geri donus penceresinde ne oldugunu goremez.
    List<Business> findByOwnerId(Long ownerId);

    // Askiya alinmis (suspendedAt dolu) bir isletme ne aramada ne kategori
    // filtresinde gorunmemeli (Faz 3.9) -- musteri gormemeli, silinmis
    // sahibin isletmesine yeni randevu denemesi zaten hic teklif edilmemeli.
    List<Business> findByCategoryAndSuspendedAtIsNull(BusinessCategory category);

    List<Business> findBySuspendedAtIsNull();

    // Kapak fotografi degistirme (BusinessService.swapPhotoKey) icin --
    // SELECT ... FOR UPDATE ile satiri kilitler. Eszamanli iki yukleme ayni
    // eski photo_key'i okuyup ikisi de kendi yeni dosyasini referanssiz
    // birakmasin diye (bkz. plan "Isletme Kapak Fotografi" madde 7). Sadece
    // bu ozel durum icin -- normal findById kilitsiz kalmaya devam ediyor.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Business b where b.id = :id")
    Optional<Business> findByIdForUpdate(@Param("id") Long id);

    // Faz 2.8: konum bazlı aramanın ucuz ön filtresi (bounding box).
    // Gerçek (dairesel) mesafe hesabı ve kesin filtreleme LocationService'te
    // Java tarafında yapılıyor -- bu sorgu sadece adayları daraltıyor, kutu
    // köşelerinde dairenin dışında kalan noktalar da dönebilir (bilerek,
    // bkz. LocationService).
    // Faz 3.9: askidaki isletmeler konum aramasinda da GORUNMEMELI.
    List<Business> findByLatitudeBetweenAndLongitudeBetweenAndSuspendedAtIsNull(
            Double minLatitude, Double maxLatitude, Double minLongitude, Double maxLongitude);
}
