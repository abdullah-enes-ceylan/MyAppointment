package com.randevu.backend.dto.request;

import com.randevu.backend.entity.BusinessCategory;
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
}
