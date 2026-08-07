package com.randevu.backend.controller;

import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.service.AppointmentService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // 1. Randevu Oluşturma
    // URL: POST /api/appointments/create
    // Body: { "customerId": 1, "businessId": 1, "serviceId": 1, "appointmentDate":
    // "2024-01-15T14:30:00" }
    @PostMapping("/create")
    public Appointment createAppointment(@RequestBody AppointmentRequest request) {
        // Artık request'in içindeki id'leri güvenle çekebiliriz
        return appointmentService.createAppointment(
                request.getCustomerId(),
                request.getBusinessId(),
                request.getServiceId(),
                request.getAppointmentDate());
    }

    // Yardımcı Request Yapısı
    static class AppointmentRequest {
        public Long customerId;
        public Long businessId;
        public Long serviceId;
        public LocalDateTime appointmentDate;

        // Getters and Setters (Gerekli olabilir)
        public Long getCustomerId() {
            return customerId;
        }

        public void setCustomerId(Long customerId) {
            this.customerId = customerId;
        }

        public Long getBusinessId() {
            return businessId;
        }

        public void setBusinessId(Long businessId) {
            this.businessId = businessId;
        }

        public Long getServiceId() {
            return serviceId;
        }

        public void setServiceId(Long serviceId) {
            this.serviceId = serviceId;
        }

        public LocalDateTime getAppointmentDate() {
            return appointmentDate;
        }

        public void setAppointmentDate(LocalDateTime appointmentDate) {
            this.appointmentDate = appointmentDate;
        }
    }

    // 2. Dükkanın Randevularını Listeleme
    // URL: GET /api/appointments/business/1
    @GetMapping("/business/{businessId}")
    public List<Appointment> getBusinessAppointments(@PathVariable Long businessId) {
        return appointmentService.getBusinessAppointments(businessId);
    }

    // 3. Müşterinin Randevularını Listeleme
    // URL: GET /api/appointments/customer/1
    @GetMapping("/customer/{customerId}")
    public List<Appointment> getCustomerAppointments(@PathVariable Long customerId) {
        return appointmentService.getCustomerAppointments(customerId);
    }

    // 4. Randevu Durumunu Güncelleme
    // URL: PUT /api/appointments/1/approve
    // Veya: PUT /api/appointments/1/reject
    @PutMapping("/{appointmentId}/{action}")
    public Appointment updateStatus(@PathVariable Long appointmentId, @PathVariable String action) {
        if (action.equalsIgnoreCase("approve")) {
            return appointmentService.updateAppointmentStatus(appointmentId, AppointmentStatus.APPROVED);
        } else if (action.equalsIgnoreCase("reject")) {
            return appointmentService.updateAppointmentStatus(appointmentId, AppointmentStatus.REJECTED);
        } else if (action.equalsIgnoreCase("cancel")) {
            return appointmentService.updateAppointmentStatus(appointmentId, AppointmentStatus.CANCELLED);
        }
        // Başka bir şey gelirse hata döndür
        throw new IllegalArgumentException("Geçersiz işlem: " + action);
    }

    // --- YENİ EKLENECEK METHODLAR ---

    // 5. Kullanıcının Yaklaşan Randevularını Listeleme (Geçmişi Değil)
    @GetMapping("/customer/{customerId}/upcoming")
    public List<Appointment> getUpcomingCustomerAppointments(@PathVariable Long customerId) {
        return appointmentService.getUpcomingCustomerAppointments(customerId);
    }

    // 6. Dükkanın Yaklaşan ve Onay Bekleyen Randevularını Listeleme
    @GetMapping("/business/{businessId}/upcoming")
    public List<Appointment> getUpcomingBusinessAppointments(@PathVariable Long businessId) {
        return appointmentService.getUpcomingBusinessAppointments(businessId);
    }

}
