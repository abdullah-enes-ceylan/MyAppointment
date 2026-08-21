package com.randevu.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Bir günün çalışma saatini ayarlamak/güncellemek için. openTime/closeTime
// bilerek @NotNull DEĞİL — closed=true gönderildiğinde ikisi de boş
// olabilir (o gün hiç açılmıyor). Bu ikisinin tutarlılığı (closed=false
// iken ikisi de dolu olmalı, openTime<closeTime olmalı) WorkingHourService'te
// kontrol ediliyor — Bean Validation tek başına bu "alanlar arası" kuralı
// temiz ifade edemiyor.
//
// Alan adı bilerek "closed", "isClosed" DEĞİL: Lombok bir "isClosed" boolean
// alanı için isClosed() getter + setClosed(boolean) setter üretir — ikisi de
// "is" önekini düşürüp JSON property adını "closed" yapar. Alanın kendisini
// "closed" adlandırmak bu uyumsuzluğu (istemcinin "closed" mi "isClosed" mi
// göndereceği belirsizliği) baştan ortadan kaldırıyor.
@Getter
@Setter
public class WorkingHourRequest {

    @NotNull(message = "Gün belirtilmelidir.")
    private DayOfWeek dayOfWeek;

    private LocalTime openTime;
    private LocalTime closeTime;
    private boolean closed;
}
