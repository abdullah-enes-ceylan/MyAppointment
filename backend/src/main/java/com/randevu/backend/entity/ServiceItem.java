package com.randevu.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class ServiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private int durationInMinutes;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;
}
