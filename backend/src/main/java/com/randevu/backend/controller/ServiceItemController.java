package com.randevu.backend.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.randevu.backend.dto.response.ServiceItemResponse;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.User;
import com.randevu.backend.mapper.ServiceItemMapper;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.OwnershipGuard;
import com.randevu.backend.service.ServiceItemService;

import java.util.List;

@RestController
@RequestMapping("/api/service-items")
public class ServiceItemController {

    private final ServiceItemService serviceItemService;
    private final CurrentUserService currentUserService;
    private final OwnershipGuard ownershipGuard;

    public ServiceItemController(ServiceItemService serviceItemService,
                                  CurrentUserService currentUserService,
                                  OwnershipGuard ownershipGuard) {
        this.serviceItemService = serviceItemService;
        this.currentUserService = currentUserService;
        this.ownershipGuard = ownershipGuard;
    }

    @GetMapping
    public List<ServiceItemResponse> getAllServiceItems() {
        return serviceItemService.getAllServiceItems().stream()
                .map(ServiceItemMapper::toResponse)
                .toList();
    }

    @GetMapping("/business/{businessId}")
    public List<ServiceItemResponse> getServiceItemsByBusiness(@PathVariable Long businessId) {
        return serviceItemService.getServicesByBusiness(businessId).stream()
                .map(ServiceItemMapper::toResponse)
                .toList();
    }

    // Yetki kontrolü olmadan, giriş yapmış HERHANGİ bir kullanıcı başka bir
    // işletmeye hizmet ekleyebiliyordu. businessId path'te olduğu için
    // doğrudan OwnershipGuard.assertOwnsBusiness kullanılabiliyor.
    @PostMapping("/create/{businessId}")
    public ServiceItemResponse createServiceItem(@PathVariable Long businessId,
                                          @RequestBody ServiceItem serviceItem,
                                          Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsBusiness(currentUser.getId(), businessId);
        return ServiceItemMapper.toResponse(serviceItemService.createServiceItem(businessId, serviceItem));
    }

    // update/delete uçlarında businessId path'te yok, sadece serviceId var —
    // bu yüzden assertOwnsServiceItem kullanılıyor (hizmeti bulup hangi
    // işletmeye ait olduğunu kendi içinde çözüyor). Bu kontrol olmadan,
    // giriş yapmış herhangi bir müşteri rakip işletmenin fiyatını
    // değiştirebiliyor ya da hizmetini silebiliyordu.
    @PutMapping("/update/{serviceId}")
    public ServiceItemResponse updateServiceItem(@PathVariable Long serviceId,
                                          @RequestBody ServiceItem serviceItem,
                                          Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsServiceItem(currentUser.getId(), serviceId);
        return ServiceItemMapper.toResponse(serviceItemService.updateService(serviceId, serviceItem));
    }

    @DeleteMapping("/delete/{serviceId}")
    public void deleteServiceItem(@PathVariable Long serviceId, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsServiceItem(currentUser.getId(), serviceId);
        serviceItemService.deleteService(serviceId);
    }

}
