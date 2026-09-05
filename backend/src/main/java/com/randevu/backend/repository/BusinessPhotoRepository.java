package com.randevu.backend.repository;

import com.randevu.backend.entity.BusinessPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessPhotoRepository extends JpaRepository<BusinessPhoto, Long> {

    // Tekil bir isletmenin TUM fotograflarini sirali dondurur (detay sayfasi
    // carousel'i icin) -- id ile ikincil siralama, ayni display_order'a
    // sahip iki satir olsa bile (kilit altinda olmamali ama garanti) sonuc
    // HER ZAMAN deterministik: carousel'de rastgele sira degismez.
    List<BusinessPhoto> findByBusinessIdOrderByDisplayOrderAscIdAsc(Long businessId);

    // Liste uclarindaki (ana sayfa, kategori, yakinimdakiler) N+1'i onlemek
    // icin TOPLU sorgu -- N isletme icin ISLETME SAYISINDAN BAGIMSIZ, TEK SQL
    // sorgusu (bkz. NOTLAR.md "N+1" notu). Kapak icin ayrica bir "sadece en
    // kucuk display_order'i getir" sorgusu YAZILMADI -- her isletmenin TUM
    // fotograflarini tek seferde cekip cagiran tarafta (BusinessController)
    // business.id'ye gore gruplamak hem daha basit hem ayni sorgu sayisi
    // garantisini veriyor (satir sayisi degil sorgu sayisi onemli); kapak,
    // her grubun ilk elemani (SQL zaten display_order, id ile sirali).
    List<BusinessPhoto> findByBusinessIdInOrderByBusinessIdAscDisplayOrderAscIdAsc(List<Long> businessIds);

    // Silme ucunun IDOR'a kapali olmasi icin: fotografin GERCEKTEN bu
    // isletmeye ait oldugunu ayni sorguda dogruluyoruz (bkz.
    // BusinessPhotoService.removePhoto, OwnershipGuard.assertOwnsActiveBusinessPhoto
    // ile AYNI kontrolun ikinci, savunma amacli katmani).
    Optional<BusinessPhoto> findByIdAndBusinessId(Long id, Long businessId);

    long countByBusinessId(Long businessId);

    // Yeni fotografin display_order'ini belirlemek icin: MAX+1, count DEGIL --
    // silme sonrasi bosluklu siralamada (0, 2, 3) count()==3 ile devam
    // etseydik yeni satir order=3'e carpar, VAR OLAN satirla CAKISIRDI.
    Optional<BusinessPhoto> findTopByBusinessIdOrderByDisplayOrderDesc(Long businessId);
}
