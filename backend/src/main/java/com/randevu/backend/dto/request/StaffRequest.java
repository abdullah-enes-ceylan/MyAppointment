package com.randevu.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

// Personel olusturma/guncelleme icin. serviceIds bilerek List<Long> --
// istemci hangi hizmetleri bu personelin verdigini id listesiyle bildiriyor,
// StaffService bu id'lerin GERCEKTEN bu isletmeye ait oldugunu dogruluyor
// (aksi halde bir isletme sahibi baska bir isletmenin serviceId'sini
// tahmin ederek personeline atayabilirdi).
@Getter
@Setter
public class StaffRequest {

    @NotBlank(message = "Personel adı belirtilmelidir.")
    private String name;

    private List<Long> serviceIds;
}
