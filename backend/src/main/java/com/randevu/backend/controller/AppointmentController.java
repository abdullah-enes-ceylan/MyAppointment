package com.randevu.backend.controller;

import com.randevu.backend.dto.response.AppointmentResponse;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.mapper.AppointmentMapper;
import com.randevu.backend.repository.AppointmentRepository;
import com.randevu.backend.service.AppointmentService;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.OwnershipGuard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentRepository appointmentRepository;
    private final CurrentUserService currentUserService;
    private final OwnershipGuard ownershipGuard;

    public AppointmentController(AppointmentService appointmentService,
                                 AppointmentRepository appointmentRepository,
                                 CurrentUserService currentUserService,
                                 OwnershipGuard ownershipGuard) {
        this.appointmentService = appointmentService;
        this.appointmentRepository = appointmentRepository;
        this.currentUserService = currentUserService;
        this.ownershipGuard = ownershipGuard;
    }

    // 1. Randevu Oluşturma — Token'dan müşteri kimliği alınır
    // URL: POST /api/appointments/create
    // Body: { "businessId": 1, "serviceId": 1, "appointmentDate": "2024-01-15T14:30:00" }
    @PostMapping("/create")
    public ResponseEntity<AppointmentResponse> createAppointment(@Valid @RequestBody AppointmentRequest request,
                                               Authentication authentication) {

        User customer = currentUserService.getCurrentUser(authentication);

        Business business = new Business();
        business.setId(request.getBusinessId());

        ServiceItem serviceItem = new ServiceItem();
        serviceItem.setId(request.getServiceId());

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setBusiness(business);
        appointment.setServiceItem(serviceItem);
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setStatus(AppointmentStatus.PENDING);

        Appointment created = appointmentService.createAppointment(appointment);
        return ResponseEntity.ok(AppointmentMapper.toResponse(created));
    }

    // Yardımcı Request Yapısı — customerId kaldırıldı, artık token'dan alınıyor
    static class AppointmentRequest {
        @NotNull(message = "İşletme seçilmelidir.")
        public Long businessId;

        @NotNull(message = "Hizmet seçilmelidir.")
        public Long serviceId;

        // @Future: geçmiş bir tarihe randevu oluşturulamaz. Eskiden bu kontrol
        // hiç yoktu — API'ye doğrudan istek atarak dünkü bir saate "randevu"
        // oluşturulabiliyordu.
        @NotNull(message = "Randevu tarihi belirtilmelidir.")
        @Future(message = "Randevu tarihi geçmişte olamaz.")
        public LocalDateTime appointmentDate;

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

    // 2. Dükkanın Randevularını Listeleme — SADECE o dükkanın sahibi görebilir.
    // OwnershipGuard olmadan, giriş yapmış HERHANGİ bir kullanıcı businessId'yi
    // değiştirerek rakip işletmenin müşteri listesini (ad, telefon, randevu
    // geçmişi) okuyabiliyordu.
    @GetMapping("/business/{businessId}")
    public List<AppointmentResponse> getBusinessAppointments(@PathVariable Long businessId, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsBusiness(currentUser.getId(), businessId);
        return appointmentService.getBusinessAppointments(businessId).stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    // 2.1 İşletmenin Onay Bekleyen Randevuları — İstek Kutusu (Inbox)
    @GetMapping("/business/{businessId}/pending")
    public ResponseEntity<List<AppointmentResponse>> getPendingAppointments(@PathVariable Long businessId, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsBusiness(currentUser.getId(), businessId);
        List<AppointmentResponse> pending = appointmentService.getPendingAppointmentsForBusiness(businessId).stream()
                .map(AppointmentMapper::toResponse)
                .toList();
        return ResponseEntity.ok(pending);
    }

    // 3. Kendi Randevularımı Listeleme — eskiden /customer/{customerId} idi ve
    // yolundaki ID herhangi bir sayıyla değiştirilerek başka bir müşterinin
    // randevu geçmişi okunabiliyordu (IDOR). Artık kimlik path'ten değil,
    // daima token'dan geliyor — kullanıcı sadece KENDİ randevularını görebilir.
    @GetMapping("/me")
    public List<AppointmentResponse> getMyAppointments(Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        return appointmentService.getCustomerAppointments(currentUser.getId()).stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    // 4. Randevu Durumunu Güncelleme
    // approve/reject → yalnızca işletme sahibi
    // cancel → işletme sahibi VEYA randevu sahibi müşteri
    @PutMapping("/{appointmentId}/{action}")
    public ResponseEntity<?> updateStatus(@PathVariable Long appointmentId,
                                          @PathVariable String action,
                                          Authentication authentication) {

        User currentUser = currentUserService.getCurrentUser(authentication);

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Randevu bulunamadı."));

        boolean isBusinessOwner = appointment.getBusiness().getOwner().getId().equals(currentUser.getId());
        boolean isCustomer = appointment.getCustomer().getId().equals(currentUser.getId());

        if (action.equalsIgnoreCase("approve") || action.equalsIgnoreCase("reject")) {
            // Onay ve ret yalnızca işletme sahibinin yetkisinde
            if (!isBusinessOwner) {
                throw new AccessDeniedException("Bu işlemi yalnızca işletme sahibi yapabilir.");
            }
            AppointmentStatus status = action.equalsIgnoreCase("approve")
                    ? AppointmentStatus.APPROVED
                    : AppointmentStatus.REJECTED;
            Appointment updated = appointmentService.updateAppointmentStatus(appointmentId, status);
            return ResponseEntity.ok(AppointmentMapper.toResponse(updated));

        } else if (action.equalsIgnoreCase("cancel")) {
            // İptal: işletme sahibi veya randevu sahibi müşteri yapabilir
            if (!isBusinessOwner && !isCustomer) {
                throw new AccessDeniedException("Bu randevuyu iptal etme yetkiniz yok.");
            }
            Appointment updated = appointmentService.updateAppointmentStatus(appointmentId, AppointmentStatus.CANCELLED);
            return ResponseEntity.ok(AppointmentMapper.toResponse(updated));
        }

        return ResponseEntity.badRequest().body("Geçersiz işlem: " + action);
    }

    // 5. Kendi Yaklaşan Randevularımı Listeleme — /customer/{id}/upcoming ile
    // aynı IDOR sorununu taşıyordu, aynı sebeple /me/upcoming'e taşındı.
    @GetMapping("/me/upcoming")
    public List<AppointmentResponse> getMyUpcomingAppointments(Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        return appointmentService.getUpcomingCustomerAppointments(currentUser.getId()).stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    // 6. Dükkanın Yaklaşan ve Onay Bekleyen Randevularını Listeleme
    @GetMapping("/business/{businessId}/upcoming")
    public List<AppointmentResponse> getUpcomingBusinessAppointments(@PathVariable Long businessId, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsBusiness(currentUser.getId(), businessId);
        return appointmentService.getUpcomingBusinessAppointments(businessId).stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    // 7. BOŞ SAATLERİ GETİRME UÇ NOKTASI — bilerek herkese açık (permitAll).
    // Randevu almadan önce müşterinin müsait saatleri görebilmesi gerekiyor,
    // bu yüzden kimlik doğrulaması istemiyoruz; işletmenin kendi hassas verisi
    // (müşteri listesi vb.) burada dönmüyor, sadece boş saat listesi dönüyor.
    @GetMapping("/available-slots")
    public ResponseEntity<List<LocalTime>> getAvailableTimeSlots(
            @RequestParam Long businessId,
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<LocalTime> availableSlots = appointmentService.getAvailableTimeSlots(businessId, serviceId, date);
        return ResponseEntity.ok(availableSlots);
    }
}
