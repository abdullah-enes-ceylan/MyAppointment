package com.randevu.backend.service;

import com.randevu.backend.dto.request.ServiceItemRequest;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.mapper.ServiceItemMapper;
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

    // getAllServiceItems() kaldirildi -- tek cagirani, silinen
    // "GET /api/service-items" ucuydu (bkz. ServiceItemController).

    // Entity artik istemciden GELMIYOR, burada mapper ile sifirdan kuruluyor
    // -- yani id daima null, save() daima INSERT. Eskiden ham ServiceItem
    // baglaniyordu ve govdeye konan bir "id" save()'i MERGE'e cevirip baska
    // bir isletmenin hizmetini eziyordu (bkz. ServiceItemRequest'teki aciklama).
    public ServiceItem createServiceItem(Long businessId, ServiceItemRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Dükkan bulunamadı"));

        ServiceItem serviceItem = ServiceItemMapper.toEntity(request);
        serviceItem.setBusiness(business);
        return serviceItemRepository.save(serviceItem);
    }

    // Musteriye gosterilecek liste — silinmis (isActive=false) hizmetler
    // otomatik disarida kalir, randevu almak icin secilemezler.
    public List<ServiceItem> getServicesByBusiness(Long businessId) {
        return serviceItemRepository.findByBusinessIdAndIsActiveTrue(businessId);
    }

    // Guncellenen entity DAIMA veritabanindan yuklenen; istemciden gelen
    // DTO sadece degerleri tasiyor. Eskiden buraya ham ServiceItem entity'si
    // geliyordu -- alanlari elle kopyaladigi icin GUVENLIYDI ama bu tesaduf
    // eseriydi: serviste istemci kontrollu id tasiyan detached bir entity
    // dolasiyordu ve birinin onu (create'te oldugu gibi) dogrudan save()'e
    // vermesi aciği geri getirmeye yeterdi. Artik oyle bir nesne yok.
    public ServiceItem updateService(Long serviceId, ServiceItemRequest request) {
        ServiceItem existingService = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Servis bulunamadı"));

        ServiceItemMapper.applyToEntity(request, existingService);
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
