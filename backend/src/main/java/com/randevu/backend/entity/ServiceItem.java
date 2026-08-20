package com.randevu.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;

@Entity
@Table(name = "service_items")
@Getter
@Setter
// @JsonCreator burada onemli: Jackson, birden fazla constructor gordugunde
// (NoArgsConstructor + AllArgsConstructor) hangisini JSON'dan nesne
// olustururken kullanacagina kendi sezgisiyle karar veriyordu ve
// @AllArgsConstructor'i seciyordu. isActive gibi primitive bir alan
// icin bu, istek govdesinde o alan hic gecmese bile Jackson'in onu
// constructor'a null olarak basmaya calisip patlamasina yol aciyordu
// (business gibi nesne tipli alanlarda sorun cikmiyordu cunku null
// bir nesne icin gecerli bir deger). Bu anotasyon Jackson'a "deserialize
// ederken DAIMA bu (no-args) constructor'i kullan, sonra setter'larla
// doldur" diyor — boylece hicbir alan constructor parametresi olarak
// zorunlu hale gelmiyor.
@NoArgsConstructor(onConstructor_ = @JsonCreator)
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

    // double degil BigDecimal: para kayan noktali sayiyla tutulmaz. double
    // ikili (binary) tabanda calisir, 0.1 gibi ondalik degerleri TAM olarak
    // temsil edemez (0.1 + 0.2 == 0.30000000000000004 gibi klasik hata) --
    // fiyat toplama/indirim gibi islemler eklendiginde bu sessizce kurus
    // farklariyla birikir. BigDecimal ondalik tabanda calisir, kesin.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int durationInMinutes;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    @JsonIgnore
    private Business business;

    // Soft delete bayrağı. Bir hizmet silindiğinde satır veritabanından
    // KALDIRILMIYOR, sadece isActive=false yapılıyor — çünkü geçmiş
    // randevular (Appointment.serviceItem) bu satıra hâlâ FK ile referans
    // veriyor. Gerçek DELETE, o randevuların referans verdiği satırı
    // kırar (DataIntegrityViolationException) ya da geçmiş randevu
    // kayıtlarının hizmet bilgisini kaybetmesine yol açar.
    // @Builder.Default şart: Lombok'un @Builder'ı olmadan, builder() ile
    // oluşturulan bir nesnede bu alan varsayılan olarak false (primitive
    // varsayılanı) olurdu, "true" alan tanımındaki değer YOK SAYILIRDI.
    // @JsonIgnore de şart: bu alan business gibi TAMAMEN sistem kontrolünde
    // olmalı — istemci create/update isteğinde bunu göndermemeli/gönderememeli
    // (sadece dedicated DELETE endpoint'i değiştirir). @JsonIgnore olmadan,
    // ServiceItem doğrudan @RequestBody olarak kullanıldığı için (henüz DTO
    // yok — Faz 1.1'de gelecek) Jackson bu alanı JSON'dan bağlamaya çalışıyor;
    // istek gövdesinde "isActive" hiç yoksa null'ı primitive boolean'a
    // basmaya çalışıp HttpMessageNotReadableException fırlatıyordu.
    // Not: @JsonIgnore'u SADECE alana koymak yetmiyor. Lombok, "isActive"
    // adlı bir boolean alan için "is" önekini düşürüp isActive() adında bir
    // getter üretir; Jackson bunu "active" adlı bir property sanır ve alanın
    // üzerindeki ignore ile eşleştiremeyebilir. @Getter(onMethod_=...) ile
    // anotasyonu doğrudan üretilen metodun üzerine koyuyoruz.
    @Builder.Default
    @Getter(onMethod_ = @JsonIgnore)
    @Column(nullable = false)
    private boolean isActive = true;
}
