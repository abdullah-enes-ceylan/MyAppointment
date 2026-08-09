package com.randevu.backend.controller;

import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.User;
import com.randevu.backend.service.AppointmentService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
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

        // 1. Gelen ID'leri kullanarak referans nesnelerini oluşturuyoruz
        User customer = new User();
        customer.setId(request.getCustomerId());

        Business business = new Business();
        business.setId(request.getBusinessId());

        ServiceItem serviceItem = new ServiceItem();
        serviceItem.setId(request.getServiceId());

        // 2. Ana Appointment nesnesini oluşturup içini dolduruyoruz
        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setBusiness(business);
        appointment.setServiceItem(serviceItem);
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setStatus(AppointmentStatus.PENDING); // Varsayılan durum: Onay Bekliyor

        // 3. Tek parça haline getirdiğimiz nesneyi servise yolluyoruz
        return appointmentService.createAppointment(appointment);
    }

    // Yardımcı Request Yapısı
    static class AppointmentRequest {
        public Long customerId;
        public Long businessId;
        public Long serviceId;
        public LocalDateTime appointmentDate;

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
    @GetMapping("/business/{businessId}")
    public List<Appointment> getBusinessAppointments(@PathVariable Long businessId) {
        return appointmentService.getBusinessAppointments(businessId);
    }

    // 3. Müşterinin Randevularını Listeleme
    @GetMapping("/customer/{customerId}")
    public List<Appointment> getCustomerAppointments(@PathVariable Long customerId) {
        return appointmentService.getCustomerAppointments(customerId);
    }

    // 4. Randevu Durumunu Güncelleme
    @PutMapping("/{appointmentId}/{action}")
    public Appointment updateStatus(@PathVariable Long appointmentId, @PathVariable String action) {
        if (action.equalsIgnoreCase("approve")) {
            return appointmentService.updateAppointmentStatus(appointmentId, AppointmentStatus.APPROVED);
        } else if (action.equalsIgnoreCase("reject")) {
            return appointmentService.updateAppointmentStatus(appointmentId, AppointmentStatus.REJECTED);
        } else if (action.equalsIgnoreCase("cancel")) {
            return appointmentService.updateAppointmentStatus(appointmentId, AppointmentStatus.CANCELLED);
        }
        throw new IllegalArgumentException("Geçersiz işlem: " + action);
    }

    // 5. Kullanıcının Yaklaşan Randevularını Listeleme
    @GetMapping("/customer/{customerId}/upcoming")
    public List<Appointment> getUpcomingCustomerAppointments(@PathVariable Long customerId) {
        return appointmentService.getUpcomingCustomerAppointments(customerId);
    }

    // 6. Dükkanın Yaklaşan ve Onay Bekleyen Randevularını Listeleme
    @GetMapping("/business/{businessId}/upcoming")
    public List<Appointment> getUpcomingBusinessAppointments(@PathVariable Long businessId) {
        return appointmentService.getUpcomingBusinessAppointments(businessId);
    }

    // 7. BOŞ SAATLERİ GETİRME UÇ NOKTASI
    @GetMapping("/available-slots")
    public ResponseEntity<?> getAvailableTimeSlots(
            @RequestParam Long businessId,
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        try {
            List<LocalTime> availableSlots = appointmentService.getAvailableTimeSlots(businessId, serviceId, date);
            return ResponseEntity.ok(availableSlots);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}