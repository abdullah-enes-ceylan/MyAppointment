package com.randevu.backend.service;

import com.randevu.backend.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// Bir gündeki boş randevu saatlerini hesaplayan saf algoritma. Bilerek JPA
// entity'lerine (Appointment, Business, ServiceItem) hiç bağımlı değil —
// sadece LocalDate/LocalTime ve BusyInterval ile çalışıyor. Bunun iki
// somut faydası var:
//   1. Birim testi yazmak için Hibernate/Spring context'i ayağa kaldırmaya
//      hiç gerek yok — sadece bu sınıfı "new" ile kurup çağırmak yeterli.
//   2. Faz 2'de bu algoritma personel bazlı çalışacak: çağıran taraf
//      (AppointmentService veya sonrasında personel-bazlı bir servis)
//      hangi randevuların "meşgul" sayılacağına karar verip listeyi
//      BusyInterval olarak buraya veriyor — bu sınıfın kendisi hiç
//      değişmeden hem tek-işletme hem personel-bazlı senaryoda çalışabilir.
@Service
public class AvailabilityCalculator {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityCalculator.class);
    private static final int DEFAULT_GRANULARITY_MINUTES = 15;

    // Slotların ilerlediği sabit adım (ızgara aralığı). Eskiden işaretçi
    // hizmetin KENDİ süresi kadar ilerliyordu — 45 dk'lık bir hizmet için
    // slotlar hep açılış saatinden itibaren 45'er dakika arayla üretiliyordu
    // (09:00, 09:45, 10:30...), 09:15'te boşluk olsa bile hiç önerilmiyordu.
    // application.properties'ten okunuyor (app.scheduling.slot-granularity-minutes)
    // — kod değiştirmeden, ortam bazında ayarlanabilir.
    @Value("${app.scheduling.slot-granularity-minutes:15}")
    private int slotGranularityMinutes;

    // Bu algoritmanın anladığı tek "meşgul zaman" birimi. Appointment
    // entity'sinden bağımsız — çağıran taraf randevuyu (ya da ileride
    // personelin başka bir işi) bu basit aralığa çevirip veriyor.
    public record BusyInterval(LocalDateTime start, LocalDateTime end) {
    }

    // Belirtilen gün, çalışma saatleri ve hizmet süresi için uygun boş
    // saat dilimlerini hesaplar. busyIntervals listesi sıralı olmak
    // zorunda değil — her ızgara noktası bağımsız olarak tüm meşgul
    // aralıklara karşı kontrol ediliyor (eskiden sıralamaya dayanan bir
    // "çakışma bulunca işaretçiyi oraya zıpla" optimizasyonu vardı; sabit
    // ızgarada buna gerek kalmadı, döngü zaten iş saatleri / ızgara adımı
    // kadar sınırlı, örn. 11 saat / 15 dk = 44 adım).
    public List<LocalTime> calculate(LocalDate date, LocalTime openTime, LocalTime closeTime,
            int serviceDurationMinutes, List<BusyInterval> busyIntervals) {

        if (serviceDurationMinutes <= 0) {
            throw new BusinessRuleException("Bu hizmetin süresi geçersiz, müsaitlik hesaplanamaz.");
        }

        int granularity = effectiveGranularity();

        LocalDateTime startOfDay = date.atTime(openTime);
        LocalDateTime endOfDay = date.atTime(closeTime);

        List<LocalTime> availableSlots = new ArrayList<>();
        LocalDateTime currentPointer = startOfDay;

        while (!currentPointer.plusMinutes(serviceDurationMinutes).isAfter(endOfDay)) {
            // currentPointer döngü boyunca yeniden atanıyor (effectively final
            // değil), lambda içinde yakalanabilmesi için o adıma özel sabit
            // bir kopyaya ihtiyaç var.
            final LocalDateTime slotStart = currentPointer;
            final LocalDateTime proposedEnd = currentPointer.plusMinutes(serviceDurationMinutes);

            boolean overlapsAny = busyIntervals.stream()
                    .anyMatch(busy -> slotStart.isBefore(busy.end()) && proposedEnd.isAfter(busy.start()));

            if (!overlapsAny) {
                availableSlots.add(slotStart.toLocalTime());
            }

            currentPointer = currentPointer.plusMinutes(granularity);
        }

        return availableSlots;
    }

    // app.scheduling.slot-granularity-minutes elle 0 ya da negatif girilirse
    // (ops hatası, kullanıcı girdisi değil) döngü hiç ilerlemez ve sonsuza
    // kalır (bkz. K5 — aynı sınıf hatanın servis süresi versiyonu). Kullanıcı
    // girdisi olmadığı için exception fırlatmak yerine güvenli varsayılana
    // düşüp logluyoruz — tek bir yanlış ayar tüm istekleri 500'e düşürmesin.
    private int effectiveGranularity() {
        if (slotGranularityMinutes <= 0) {
            log.warn("app.scheduling.slot-granularity-minutes geçersiz ({}), varsayılan {} kullanılıyor",
                    slotGranularityMinutes, DEFAULT_GRANULARITY_MINUTES);
            return DEFAULT_GRANULARITY_MINUTES;
        }
        return slotGranularityMinutes;
    }
}
