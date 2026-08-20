package com.randevu.backend.service;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServiceItemService {

    private final ServiceItemRepository serviceItemRepository;
    private final BusinessRepository businessRepository;

    public ServiceItemService(ServiceItemRepository serviceItemRepository, BusinessRepository businessRepository) {
        this.serviceItemRepository = serviceItemRepository;
        this.businessRepository = businessRepository;
    }

    public List<ServiceItem> getAllServiceItems() {
        return serviceItemRepository.findAll();
    }

    public ServiceItem createServiceItem(Long businessId, ServiceItem serviceItem) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Dükkan bulunamadı"));
        serviceItem.setBusiness(business);
        return serviceItemRepository.save(serviceItem);
    }

    public List<ServiceItem> getServicesByBusiness(Long businessId) {
        return serviceItemRepository.findByBusinessId(businessId);
    }

    public ServiceItem updateService(Long serviceId, ServiceItem serviceItem) {
        ServiceItem existingService = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Servis bulunamadı"));

        existingService.setName(serviceItem.getName());
        existingService.setDescription(serviceItem.getDescription()); // Açıklama güncellemesi eklendi
        existingService.setPrice(serviceItem.getPrice());
        existingService.setDurationInMinutes(serviceItem.getDurationInMinutes()); // Entity'deki doğru isimle
                                                                                  // değiştirildi

        return serviceItemRepository.save(existingService);
    }

    // deleteById tek basina var olmayan bir ID icin JPA'nin kendi
    // EmptyResultDataAccessException'ini firlatirdi — bu da GlobalExceptionHandler'in
    // catch-all'ina dusup 500 donerdi. Once var mi diye bakip anlamli bir
    // ResourceNotFoundException (404) firlatmak daha dogru.
    public void deleteService(Long serviceId) {
        if (!serviceItemRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Servis bulunamadı");
        }
        serviceItemRepository.deleteById(serviceId);
    }
}
