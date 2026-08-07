package com.randevu.backend.controller;

import org.springframework.web.bind.annotation.*;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.service.ServiceItemService;

import java.util.List;

@RestController
@RequestMapping("/api/service-items")
public class ServiceItemController {

    private final ServiceItemService serviceItemService;

    public ServiceItemController(ServiceItemService serviceItemService) {
        this.serviceItemService = serviceItemService;
    }

    @GetMapping
    public List<ServiceItem> getAllServiceItems() {
        return serviceItemService.getAllServiceItems();
    }

    @GetMapping("/business/{businessId}")
    public List<ServiceItem> getServiceItemsByBusiness(@PathVariable Long businessId) {
        return serviceItemService.getServicesByBusiness(businessId);
    }

    @PostMapping("/create/{businessId}")
    public ServiceItem createServiceItem(@PathVariable Long businessId, @RequestBody ServiceItem serviceItem) {
        return serviceItemService.createServiceItem(businessId, serviceItem);
    }

    @PutMapping("/update/{serviceId}")
    public ServiceItem updateServiceItem(@PathVariable Long serviceId, @RequestBody ServiceItem serviceItem) {
        return serviceItemService.updateService(serviceId, serviceItem);
    }

    @DeleteMapping("/delete/{serviceId}")
    public void deleteServiceItem(@PathVariable Long serviceId) {
        serviceItemService.deleteService(serviceId);
    }

}
