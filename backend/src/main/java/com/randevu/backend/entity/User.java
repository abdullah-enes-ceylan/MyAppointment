package com.randevu.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String surName;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Hesap silme akisi (Faz 3.9, KVKK unutulma hakki). Ikisi de null =
    // normal hesap. Sadece deletionRequestedAt dolu = silme istendi ama
    // ANONIMLESTIRME DAHIL hicbir sey henuz degismedi -- bu bilerek boyle,
    // AccountDeletionScheduler'in gerekcesine bakiniz: 30 gunluk pencerede
    // hesap ele gecirilip silme tetiklenmisse gercek sahibi POST
    // /api/users/me/cancel-deletion ile hesabini AYNEN biraktigi gibi
    // bulabilmeli. Ikisi de dolu = anonimlestirme fiilen calisti, GERI
    // DONUS YOK.
    @Column(name = "deletion_requested_at")
    private LocalDateTime deletionRequestedAt;

    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;
}