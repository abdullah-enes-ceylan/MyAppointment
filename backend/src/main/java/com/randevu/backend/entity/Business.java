package com.randevu.backend.entity;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    private String phone;

    private String description;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Business.java dosyasının içine eklenecek
    private LocalTime openTime; // açılış zamanı
    private LocalTime closeTime; // kapanış zamanı

    // Faz 2.8: konum bazlı arama. Nullable -- isletme sahibi konumunu
    // henuz girmemis olabilir (mevcut tum test verisi bunun ornegi),
    // bu durumda LocationService.findNearby sorgusunda bu satir hic
    // eslesmez (SQL BETWEEN NULL degerle asla eslesmez), hata vermez.
    private Double latitude;
    private Double longitude;

    // Onaylı işletme rozeti. Owner kendi kendini onaylayamaz -- BusinessRequest'e
    // BİLEREK eklenmedi, sadece DB/seed üzerinden set edilir (bkz. V8 migration).
    // @Builder.Default sart: aksi halde builder() ile olusturulan nesnede bu alan
    // her zaman false olurdu, ServiceItem.isActive'teki ayni gerekce (bkz. o dosya).
    @Builder.Default
    @Column(nullable = false)
    private boolean verified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessCategory category;

    // Kategoriden ayrı bir eksen: kategori "ne hizmeti", bu "kime".
    // Gerekçesi ServedGender enum'ında. @Builder.Default olmadan
    // builder() ile oluşturulan nesnede null kalırdı (Business.verified
    // ve ServiceItem.isActive'deki aynı Lombok tuzağı).
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "served_gender", nullable = false)
    private ServedGender servedGender = ServedGender.UNISEX;

    // V17'den itibaren BURADA YOK -- kapak fotografi artik BusinessPhoto
    // (ayri tablo, coklu fotograf) uzerinden okunuyor. businesses.photo_key
    // kolonu DB'de HALA DURUYOR (expand-contract, bkz. V17 migration) ama
    // hicbir Java kodu artik bunu okumuyor/yazmiyor -- gercekten kaldirilmasi
    // ayri, sonraki bir migration.

    // Sahibi hesap silme talep ettiginde dolar (Faz 3.9). Dolu oldugu surece
    // bu isletme aramada/listelemede GORUNMEZ, detay ucu 404 doner (bkz.
    // BusinessService.getBusinessById) -- ama sahibi kendi /my ucundan
    // GORMEYE devam eder, hesap silme talebi geri alinirsa (POST
    // /api/users/me/cancel-deletion) bu alan null'a doner.
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    // Isletme basina "otomatik onay" anahtari (bkz. CLAUDE.md karar tablosu,
    // "Randevu istek mi, direkt mi"). Varsayilan false: yeni randevu talepleri
    // Istek Kutusu'na (PENDING) duser, isletme sahibi onaylamadan hicbir sey
    // olmaz. true ise AppointmentService.createAppointment talebi dogrudan
    // APPROVED olarak olusturur -- Istek Kutusu tamamen atlanir. @Builder.Default
    // sart: verified/servedGender'daki AYNI Lombok tuzagi (builder() olmadan
    // olusturulan nesnede false olurdu zaten, ama @Builder.Default olmadan
    // builder() ile olusturulan nesnede JVM varsayilanina degil bu alana
    // yazilan degere guvenilemez).
    @Builder.Default
    @Column(name = "auto_approve", nullable = false)
    private boolean autoApprove = false;

    // Getter ve Setter metotları
    public BusinessCategory getCategory() {
        return category;
    }

    public void setCategory(BusinessCategory category) {
        this.category = category;
    }

    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<ServiceItem> serviceItems;

}