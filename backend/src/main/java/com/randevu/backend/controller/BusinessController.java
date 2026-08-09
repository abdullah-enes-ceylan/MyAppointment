package com.randevu.backend.controller;

import com.randevu.backend.entity.Business;
import com.randevu.backend.service.BusinessService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping
    public List<Business> getAllBusinesses() {
        return businessService.getAllBusinesses();
    }

    @GetMapping("/owner/{ownerId}")
    public List<Business> getBusinessesByOwner(@PathVariable Long ownerId) {
        return businessService.getBusinessesByOwner(ownerId);
    }

    @PostMapping("/create")
    public Business createBusiness(@RequestParam Long ownerId, @RequestBody Business business) {
        return businessService.createBusiness(ownerId, business);
    }

    // Belirtilen kategori adına göre işletmeleri getirir.
    @GetMapping("/category/{categoryName}")
    public ResponseEntity<List<Business>> getBusinessesByCategory(@PathVariable String categoryName) {
        List<Business> businesses = businessService.getBusinessesByCategory(categoryName);
        return ResponseEntity.ok(businesses);
    }

}