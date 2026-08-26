package com.randevu.backend.controller;

import com.randevu.backend.dto.response.AppointmentResponse;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.Staff;
import com.randevu.backend.entity.User;
import com.randevu.backend.mapper.AppointmentMapper;
import com.randevu.backend.service.AppointmentService;
import com.randevu.backend.service.AppointmentService.AppointmentAction;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.OwnershipGuard;
import com.randevu.backend.service.ReviewService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
    private final CurrentUserService currentUserService;
    private final OwnershipGuard ownershipGuard;
    private final ReviewService reviewService;

    // Not: AppointmentRepository artık burada YOK. Eskiden updateStatus
    // randevuyu doğrudan repository'den çekiyordu — controller'ın işi HTTP
    // isteğini/yanıtını çevirmek, veritabanına erişmek servisin işi (SRP).
    // Bu bağımlılığın kaldırılması, o mantığın AppointmentService.changeStatus'a
    // taşınmasının doğal bir sonucu.
    public AppointmentController(AppointmentService appointmentService,
                                 CurrentUserService currentUserService,
                                 OwnershipGuard ownershipGuard,
                                 ReviewService reviewService) {
        this.appointmentService = appointmentService;
        this.currentUserService = currentUserService;
        this.ownershipGuard = ownershipGuard;
        this.reviewService = reviewService;
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
        // status BİLEREK burada atanmıyor: randevunun hangi durumda doğduğu
        // bir iş kuralı, AppointmentService'te belirleniyor (bkz. oradaki
        // açıklama). Controller'ın işi HTTP isteğini nesneye çevirmek.
        appointment.setAppointmentDate(request.getAppointmentDate());

        // Faz 2.5: staffId opsiyonel -- frontend'de henuz personel secim
        // ekrani yok (Faz 2.9), bu yuzden simdilik hep null gelecek ve
        // AppointmentService eski (isletme capinda) cakisma kontrolune
        // duser. Alan API'de simdiden var ki Faz 2.9 geldiginde controller'a
        // dokunmaya gerek kalmasin.
        if (request.getStaffId() != null) {
            Staff staff = new Staff();
            staff.setId(request.getStaffId());
            appointment.setStaff(staff);
        }

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

        // Bilerek @NotNull DEĞİL — personel sistemi kullanmayan bir işletmede
        // (ya da müşteri "fark etmez" dediğinde) hiç gönderilmez.
        public Long staffId;

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

        public Long getStaffId() {
            return staffId;
        }

        public void setStaffId(Long staffId) {
            this.staffId = staffId;
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
        // Faz 2.10: hasReview burada gercekten hesaplaniyor (musteri
        // kendi randevularina bakiyor, "yorum yap" butonunun gorunup
        // gorunmeyecegini bilmesi gerekiyor) -- randevu basina bir sorgu
        // (N+1), bir musterinin randevu sayisi kucuk oldugu icin onemsiz.
        return appointmentService.getCustomerAppointments(currentUser.getId()).stream()
                .map(apt -> AppointmentMapper.toResponse(apt, reviewService.hasReview(apt.getId())))
                .toList();
    }

    // 4. Randevu Durumunu Güncelleme — approve/reject/cancel. Eskiden burada
    // action bir String'di, yetki kontrolü ve randevu arama burada,
    // AppointmentRepository'ye doğrudan erişerek yapılıyordu. Artık controller
    // sadece path'teki string'i enum'a çeviriyor ve servise devrediyor —
    // yetki kontrolü, durum geçiş kuralları ve veritabanı erişimi tamamen
    // AppointmentService.changeStatus'ta (bkz. oradaki açıklama).
    @PutMapping("/{appointmentId}/{action}")
    public ResponseEntity<AppointmentResponse> updateStatus(@PathVariable Long appointmentId,
                                          @PathVariable String action,
                                          Authentication authentication) {

        User currentUser = currentUserService.getCurrentUser(authentication);
        AppointmentAction parsedAction = AppointmentAction.from(action);
        Appointment updated = appointmentService.changeStatus(appointmentId, parsedAction, currentUser.getId());
        return ResponseEntity.ok(AppointmentMapper.toResponse(updated));
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
