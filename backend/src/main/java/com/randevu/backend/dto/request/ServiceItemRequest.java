package com.randevu.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// Hizmet olusturma/guncelleme istegi. Bu DTO gelmeden once her iki uc de
// HAM ServiceItem entity'sini @RequestBody olarak aliyordu ve bu GERCEK,
// SOMULEBILIR bir aciga yol aciyordu:
//
// Entity'de "business" ve "isActive" @JsonIgnore ile korunmustu ama "id"
// korunmamisti. create ucunda servis gelen nesneyi dogrudan save() ediyor;
// save() dolu bir id gorunce INSERT degil MERGE (UPDATE) yapar. Yani baska
// bir isletmenin sahibi, KENDI isletmesine hizmet ekliyormus gibi gorunen
// bir istekte govdeye "id": 6 koyarak 6 numarali hizmeti hem eziyor hem de
// business_id'sini kendi isletmesine cekiyordu. Sahiplik kontrolu bunu
// yakalayamiyordu cunku o kontrol PATH'teki businessId'ye bakiyor,
// govdedeki id'ye degil.
//
// Cozum "gelen id'yi yok say" seklinde bir kontrol EKLEMEK degil: burada
// id alani hic YOK, dolayisiyla Jackson'in baglayacagi bir hedef de yok.
// Entity daima sunucuda kuruluyor, id daima veritabanindan geliyor.
// Ayni DTO update'te de kullaniliyor -- update bugun (alanlari elle
// kopyaladigi icin) guvenli olsa bile bu TESADUFI bir guvenlik: serviste
// istemci kontrollu id tasiyan detached bir entity dolastigi surece, bir
// gun birinin onu save()'e vermesi yeterdi. Artik oyle bir nesne yok.
@Getter
@Setter
public class ServiceItemRequest {

    @NotBlank(message = "Hizmet adı boş olamaz.")
    @Size(max = 255, message = "Hizmet adı en fazla 255 karakter olabilir.")
    private String name;

    @NotBlank(message = "Açıklama boş olamaz.")
    @Size(max = 255, message = "Açıklama en fazla 255 karakter olabilir.")
    private String description;

    // BigDecimal, double degil -- para kayan noktayla tutulmaz (bkz.
    // ServiceItem entity'sindeki aciklama). inclusive=false: 0 TL'lik
    // bir hizmet anlamsiz.
    @NotNull(message = "Fiyat belirtilmelidir.")
    @DecimalMin(value = "0.0", inclusive = false, message = "Fiyat sıfırdan büyük olmalı.")
    private BigDecimal price;

    // @Positive: sifir sureli hizmet SUNUCUYU KILITLEMEZ -- AvailabilityCalculator
    // zaten "<= 0" durumunda erken cikiyor (ROADMAP 0.7'deki sonsuz dongu
    // kapatilmis). Buradaki kontrol baska bir sey icin: sifir sureli bir
    // hizmet olusturulabildiginde isletme sahibi hizmeti ekliyor, hicbir
    // musait saat gorunmuyor ve sebebini anlamiyordu. Hatayi olustugu anda
    // soylemek, sessizce calismayan bir hizmet birakmaktan iyi.
    @Positive(message = "Hizmet süresi sıfırdan büyük olmalı.")
    private int durationInMinutes;
}
