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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessCategory category;

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