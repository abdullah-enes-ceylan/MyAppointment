package com.randevu.backend.repository;

import com.randevu.backend.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    // Toggle butonunun idempotent çalışması için: eklemeden/silmeden önce
    // "zaten var mı" kontrolü.
    Optional<Favorite> findByUser_IdAndBusiness_Id(Long userId, Long businessId);

    // "Favorilerim" listesi -- business join fetch ile tek sorguda,
    // N+1'e düşmeden (BusinessMapper zaten ayrıca rating stats için
    // ek sorgu atıyor, o kısım kabul edilen N+1, bkz. BusinessController).
    List<Favorite> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // Profil ekranindaki "favori isletme sayisi" -- listeyi cekmeye gerek yok.
    long countByUser_Id(Long userId);

    // Kimlik anonimlestirmesinde favoriler HARD DELETE edilir (Faz 3.9,
    // ROADMAP 3.9) -- diger tum tablolarin aksine bunlar "silinmis
    // kullaniciya ait favori isletme" seklinde saklanmaya deger bir kayit
    // degil, tamamen kullaniciya ozel bir tercih.
    void deleteByUser_Id(Long userId);
}
