package com.randevu.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// appointmentId burada bilerek VAR -- musteri hangi randevusuna yorum
// yaptigini belirtmek zorunda (bir musterinin birden fazla tamamlanmis
// randevusu olabilir). customerId YOK: kimlik her zamanki gibi token'dan
// gelir (bkz. CLAUDE.md guvenlik kurali), body'den degil.
@Getter
@Setter
public class ReviewRequest {

    @NotNull(message = "Randevu belirtilmelidir.")
    private Long appointmentId;

    @NotNull(message = "Puan belirtilmelidir.")
    @Min(value = 1, message = "Puan en az 1 olmalıdır.")
    @Max(value = 5, message = "Puan en fazla 5 olmalıdır.")
    private Integer rating;

    @Size(max = 1000, message = "Yorum en fazla 1000 karakter olabilir.")
    private String comment;
}
