package com.randevu.backend.dto.response;

// Silme talebi ONAYLANMADAN ÖNCE onay diyaloğunda gösterilecek etki
// önizlemesi (Faz 3.9). Sadece BUSINESS_OWNER için anlamlı -- USER'da
// her zaman 0 (bkz. AccountDeletionService.previewAffectedAppointmentCount).
public record DeletionImpactResponse(int affectedAppointmentCount) {
}
