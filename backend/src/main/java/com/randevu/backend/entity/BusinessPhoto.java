package com.randevu.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Isletme fotograflarindan biri (V17 migration, bkz. o dosyanin gerekcesi).
// Business.java'da BILEREK karsilik gelen bir @OneToMany "photos" alani YOK --
// coklu fotograf her zaman BusinessPhotoRepository uzerinden, ihtiyaca gore
// (tekil isletme icin tam liste, liste ucunda TOPLU kapak sorgusu) cekiliyor.
// Boylece BusinessMapper/BusinessController yanlislikla business.getPhotos()
// cagirip liste uclarinda N+1 uretemez -- bu, bir fetch-tipi ayariyla degil,
// iliskinin o yonde hic var olmamasiyla yapisal olarak engelleniyor.
@Entity
@Table(name = "business_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    @JsonIgnore
    private Business business;

    // Depolama anahtari -- BusinessPhotoStorage'in {key}-card.jpg/{key}-detail.jpg
    // turetme kuraliyla ayni (bkz. BusinessPhotoService).
    @Column(name = "photo_key", nullable = false, length = 64)
    private String photoKey;

    // En kucuk deger = kapak (ana sayfa kartlari, sidebar avatari, favoriler
    // -- hepsi hala sadece "kapak" okuyor, coklu galeriden habersiz). Silme
    // sonrasi bosluklu kalabilir (0, 2, 3) -- BILEREK yeniden numaralanmiyor,
    // bkz. V17 migration yorumu.
    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    // Ciplak LocalDateTime.now() YASAK (bkz. CLAUDE.md "Zaman" karari) --
    // burada varsayilan deger YOK, Review/Favorite/Appointment'taki ayni
    // desen: deger BusinessPhotoService tarafindan enjekte edilen Clock
    // uzerinden aciliyor.
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
