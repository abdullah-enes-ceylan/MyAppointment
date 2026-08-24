package com.randevu.backend.entity;

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