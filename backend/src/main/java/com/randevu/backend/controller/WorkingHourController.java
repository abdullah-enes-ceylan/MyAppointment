package com.randevu.backend.controller;

import com.randevu.backend.dto.request.BusinessClosureRequest;
import com.randevu.backend.dto.request.WorkingHourRequest;
import com.randevu.backend.dto.response.BusinessClosureResponse;
import com.randevu.backend.dto.response.WorkingHourResponse;
import com.randevu.backend.entity.BusinessClosure;
import com.randevu.backend.entity.User;
import com.randevu.backend.entity.WorkingHour;
import com.randevu.backend.mapper.WorkingHourMapper;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.OwnershipGuard;
import com.randevu.backend.service.WorkingHourService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Faz 1.8'deki "İşletme paneli — çalışma saatleri ekranı"nın kullanacağı
// backend altyapısı. GET uçları herkese açık (müşteri randevu almadan önce
// "bu işletme Pazar günleri açık mı" görebilmeli); değiştiren uçlar
// OwnershipGuard ile sahiplik kontrollü.
@RestController
@RequestMapping("/api/businesses/{businessId}")
public class WorkingHourController {

    private final WorkingHourService workingHourService;
    private final CurrentUserService currentUserService;
    private final OwnershipGuard ownershipGuard;

    public WorkingHourController(WorkingHourService workingHourService,
                                  CurrentUserService currentUserService,
                                  OwnershipGuard ownershipGuard) {
        this.workingHourService = workingHourService;
        this.currentUserService = currentUserService;
        this.ownershipGuard = ownershipGuard;
    }

    @GetMapping("/working-hours")
    public List<WorkingHourResponse> getWorkingHours(@PathVariable Long businessId) {
        return workingHourService.getWorkingHours(businessId).stream()
                .map(WorkingHourMapper::toResponse)
                .toList();
    }

    // Bir günün saatini ayarlar (upsert) — o gün için kayıt yoksa oluşturur,
    // varsa günceller.
    @PutMapping("/working-hours")
    public WorkingHourResponse setWorkingHour(@PathVariable Long businessId,
                                               @Valid @RequestBody WorkingHourRequest request,
                                               Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsBusiness(currentUser.getId(), businessId);
        WorkingHour saved = workingHourService.setWorkingHour(businessId, request);
        return WorkingHourMapper.toResponse(saved);
    }

    @GetMapping("/closures")
    public List<BusinessClosureResponse> getClosures(@PathVariable Long businessId) {
        return workingHourService.getClosures(businessId).stream()
                .map(WorkingHourMapper::toResponse)
                .toList();
    }

    @PostMapping("/closures")
    public ResponseEntity<BusinessClosureResponse> addClosure(@PathVariable Long businessId,
                                                                @Valid @RequestBody BusinessClosureRequest request,
                                                                Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsBusiness(currentUser.getId(), businessId);
        BusinessClosure created = workingHourService.addClosure(businessId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkingHourMapper.toResponse(created));
    }

    @DeleteMapping("/closures/{closureId}")
    public void removeClosure(@PathVariable Long businessId, @PathVariable Long closureId,
                               Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsBusiness(currentUser.getId(), businessId);
        workingHourService.removeClosure(businessId, closureId);
    }
}
