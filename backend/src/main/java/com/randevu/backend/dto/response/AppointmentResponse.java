package com.randevu.backend.dto.response;

import com.randevu.backend.entity.AppointmentStatus;
import java.time.LocalDateTime;

// Appointment entity'sinin dışa açılan hali. Eskiden bu entity doğrudan
// dönüyordu: nested Business (owner dahil — email/telefon sızıntısı),
// nested ServiceItem, nested User customer (şifre hariç her şey). Bu
// kayıtlı bir seferde üç ayrı sızıntı kaynağıydı. Artık her iç alan kendi
// dar kapsamlı özetiyle (BusinessSummary, ServiceItemResponse,
// CustomerSummary) taşınıyor.
public record AppointmentResponse(
        Long id,
        LocalDateTime appointmentDate,
        AppointmentStatus status,
        BusinessSummary business,
        ServiceItemResponse serviceItem,
        CustomerSummary customer,
        // Faz 2.5 — null olabilir: personel atanmadan oluşturulan randevular
        // için (bkz. StaffSummary'deki açıklama).
        StaffSummary staff) {
}
