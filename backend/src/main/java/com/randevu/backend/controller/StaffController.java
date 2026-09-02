package com.randevu.backend.controller;

import com.randevu.backend.dto.request.StaffRequest;
import com.randevu.backend.dto.request.StaffWorkingHourRequest;
import com.randevu.backend.dto.response.StaffResponse;
import com.randevu.backend.dto.response.StaffWorkingHourResponse;
import com.randevu.backend.entity.Staff;
import com.randevu.backend.entity.User;
import com.randevu.backend.mapper.StaffMapper;
import com.randevu.backend.mapper.StaffWorkingHourMapper;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.OwnershipGuard;
import com.randevu.backend.service.StaffService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Faz 2.3: personel CRUD. Su an randevu akisina baglanmiyor (Faz 2.5'te
// Appointment.staff eklenince baglanacak) -- bu adim sadece isletme
// sahibinin personel listesini ve kimin hangi hizmeti verdigini
// yonetebilmesi icin.
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;
    private final CurrentUserService currentUserService;
    private final OwnershipGuard ownershipGuard;

    public StaffController(StaffService staffService, CurrentUserService currentUserService,
            OwnershipGuard ownershipGuard) {
        this.staffService = staffService;
        this.currentUserService = currentUserService;
        this.ownershipGuard = ownershipGuard;
    }

    // Sahiplik kontrolu SONRADAN eklendi: bu uc daha once giris yapmis
    // HERKESE aciкti. Musteri personeli zaten gormuyor/secmiyor (CLAUDE.md
    // karar tablosu), yani ucun herkese acik olmasinin bir karsiligi yoktu;
    // buna karsilik rakip bir isletme calisan adlarini okuyabiliyordu.
    // Calisan adi isletmenin degil, CALISANIN kisisel verisi.
    @GetMapping("/business/{businessId}")
    public List<StaffResponse> getStaffByBusiness(@PathVariable Long businessId,
            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsBusiness(currentUser.getId(), businessId);
        return staffService.getStaffByBusiness(businessId).stream()
                .map(StaffMapper::toResponse)
                .toList();
    }

    @PostMapping("/create/{businessId}")
    public ResponseEntity<StaffResponse> createStaff(@PathVariable Long businessId,
            @Valid @RequestBody StaffRequest request,
            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsActiveBusiness(currentUser.getId(), businessId);
        Staff created = staffService.createStaff(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(StaffMapper.toResponse(created));
    }

    @PutMapping("/update/{staffId}")
    public StaffResponse updateStaff(@PathVariable Long staffId,
            @Valid @RequestBody StaffRequest request,
            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsActiveStaff(currentUser.getId(), staffId);
        return StaffMapper.toResponse(staffService.updateStaff(staffId, request));
    }

    @DeleteMapping("/delete/{staffId}")
    public void deleteStaff(@PathVariable Long staffId, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsActiveStaff(currentUser.getId(), staffId);
        staffService.deleteStaff(staffId);
    }

    // Ayni sekilde sonradan korumaya alindi -- ve bu digerinden daha
    // hassas: bir calisanin haftalik mesai programi dogrudan kisisel veri.
    @GetMapping("/{staffId}/working-hours")
    public List<StaffWorkingHourResponse> getWorkingHours(@PathVariable Long staffId,
            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsStaff(currentUser.getId(), staffId);
        return staffService.getWorkingHours(staffId).stream()
                .map(StaffWorkingHourMapper::toResponse)
                .toList();
    }

    @PutMapping("/{staffId}/working-hours")
    public StaffWorkingHourResponse setWorkingHour(@PathVariable Long staffId,
            @Valid @RequestBody StaffWorkingHourRequest request,
            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsActiveStaff(currentUser.getId(), staffId);
        return StaffWorkingHourMapper.toResponse(staffService.setWorkingHour(staffId, request));
    }
}
