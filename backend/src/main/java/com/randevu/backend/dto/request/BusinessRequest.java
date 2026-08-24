package com.randevu.backend.dto.request;

import com.randevu.backend.entity.BusinessCategory;
import com.randevu.backend.entity.ServedGender;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

// İşletme oluşturma/güncelleme isteği. Eskiden hem create hem (henüz
// olmayan) update için Business entity'si doğrudan @RequestBody olarak
// kullanılıyordu — istemci "owner" veya "id" alanını gönderirse ne
// olacağı hiç düşünülmemişti (mass assignment). Bu DTO'da o alanlar
// hiç yok: sahiplik daima token'dan (CurrentUserService), id daima
// path'ten/veritabanından gelir, istemci ikisini de göndermeye çalışsa
// bile bağlanacak bir alan bulamaz.
@Getter
@Setter
public class BusinessRequest {

    @NotBlank(message = "İşletme adı boş olamaz.")
    private String name;

    @NotBlank(message = "Adres boş olamaz.")
    private String address;

    private String phone;

    private String description;

    @NotNull(message = "Açılış saati belirtilmelidir.")
    private LocalTime openTime;

    @NotNull(message = "Kapanış saati belirtilmelidir.")
    private LocalTime closeTime;

    @NotNull(message = "Kategori seçilmelidir.")
    private BusinessCategory category;

    // @NotNull: kategori kadar belirleyici bir bilgi, boş bırakılıp
    // sessizce varsayılana düşmemeli -- işletme sahibi bilinçli seçsin.
    // (DB tarafındaki DEFAULT 'UNISEX' sadece V10'daki mevcut satırları
    // doldurmak için, yeni kayıtlar bu alandan geliyor.)
    @NotNull(message = "Kime hizmet verdiğiniz belirtilmelidir.")
    private ServedGender servedGender;

    // Faz 2.8: bilerek @NotNull DEĞİL -- işletme sahibi konumunu panelde
    // ayrı bir adımda (harita üzerinden) girer, işletme oluştururken/temel
    // bilgileri güncellerken zorunlu değil.
    @DecimalMin(value = "-90", message = "Enlem -90 ile 90 arasında olmalıdır.")
    @DecimalMax(value = "90", message = "Enlem -90 ile 90 arasında olmalıdır.")
    private Double latitude;

    @DecimalMin(value = "-180", message = "Boylam -180 ile 180 arasında olmalıdır.")
    @DecimalMax(value = "180", message = "Boylam -180 ile 180 arasında olmalıdır.")
    private Double longitude;
}
