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

    // Musteriye gosterilecek liste — silinmis (isActive=false) hizmetler
    // otomatik disarida kalir, randevu almak icin secilemezler.
    public List<ServiceItem> getServicesByBusiness(Long businessId) {
        return serviceItemRepository.findByBusinessIdAndIsActiveTrue(businessId);
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

    // Gercek DELETE degil, soft delete: satiri silmek yerine isActive=false
    // yapiyoruz. Neden: bu hizmete referans veren gecmis randevular
    // (Appointment.serviceItem, nullable=false FK) var olabilir. Gercek
    // DELETE denenirse ya DB'nin FK constraint'i patlar (DataIntegrityViolationException,
    // GlobalExceptionHandler'in catch-all'inda 500'e duser) ya da o randevularin
    // hizmet bilgisi kaybolur. Soft delete ile hem gecmis kayitlar saglam kalir
    // hem de hizmet yeni randevular icin artik secilemez hale gelir
    // (bkz. getServicesByBusiness'teki isActive filtresi).
    public void deleteService(Long serviceId) {
        ServiceItem serviceItem = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Servis bulunamadı"));
        serviceItem.setActive(false);
        serviceItemRepository.save(serviceItem);
    }
}
