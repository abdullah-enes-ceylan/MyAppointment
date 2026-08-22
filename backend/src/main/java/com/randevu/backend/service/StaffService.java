package com.randevu.backend.service;

import com.randevu.backend.dto.request.StaffRequest;
import com.randevu.backend.dto.request.StaffWorkingHourRequest;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.Staff;
import com.randevu.backend.entity.StaffWorkingHour;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.StaffRepository;
import com.randevu.backend.repository.StaffWorkingHourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StaffService {

    private final StaffRepository staffRepository;
    private final BusinessRepository businessRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final StaffWorkingHourRepository staffWorkingHourRepository;

    public StaffService(StaffRepository staffRepository, BusinessRepository businessRepository,
            ServiceItemRepository serviceItemRepository, StaffWorkingHourRepository staffWorkingHourRepository) {
        this.staffRepository = staffRepository;
        this.businessRepository = businessRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.staffWorkingHourRepository = staffWorkingHourRepository;
    }

    // Musteriye/panele gosterilecek liste -- isten ayrilmis personel
    // otomatik disarida kalir (bkz. ServiceItemService.getServicesByBusiness
    // ile ayni desen).
    public List<Staff> getStaffByBusiness(Long businessId) {
        return staffRepository.findByBusinessIdAndIsActiveTrue(businessId);
    }

    @Transactional
    public Staff createStaff(Long businessId, StaffRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        Staff staff = Staff.builder()
                .business(business)
                .name(request.getName())
                .services(resolveServices(businessId, request.getServiceIds()))
                .build();

        return staffRepository.save(staff);
    }

    @Transactional
    public Staff updateStaff(Long staffId, StaffRequest request) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Personel bulunamadı."));

        staff.setName(request.getName());
        staff.setServices(resolveServices(staff.getBusiness().getId(), request.getServiceIds()));

        return staffRepository.save(staff);
    }

    // Gercek DELETE degil, soft delete -- ServiceItemService.deleteService'teki
    // ile ayni gerekce: Faz 2.5'te Appointment.staff eklendiginde gecmis
    // randevular bu satira FK ile referans veriyor olacak.
    @Transactional
    public void deleteStaff(Long staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Personel bulunamadı."));
        staff.setActive(false);
        staffRepository.save(staff);
    }

    // serviceIds'teki her id'nin GERCEKTEN bu isletmeye ait oldugunu
    // dogrular -- aksi halde bir isletme sahibi baska bir isletmenin
    // serviceId'sini tahmin ederek kendi personeline atayabilirdi (ayni
    // sinif AppointmentService.createAppointment'taki servis/isletme
    // eslesme kontrolu).
    private Set<ServiceItem> resolveServices(Long businessId, List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return new HashSet<>();
        }

        Set<ServiceItem> services = new HashSet<>(serviceItemRepository.findAllById(serviceIds));
        if (services.size() != new HashSet<>(serviceIds).size()) {
            throw new ResourceNotFoundException("Belirtilen hizmetlerden biri bulunamadı.");
        }

        boolean allBelongToBusiness = services.stream()
                .allMatch(service -> service.getBusiness().getId().equals(businessId));
        if (!allBelongToBusiness) {
            throw new BusinessRuleException("Seçilen hizmetlerden biri bu işletmeye ait değil.");
        }

        return services;
    }

    public List<StaffWorkingHour> getWorkingHours(Long staffId) {
        return staffWorkingHourRepository.findByStaffId(staffId);
    }

    // WorkingHourService.setWorkingHour ile ayni upsert deseni.
    @Transactional
    public StaffWorkingHour setWorkingHour(Long staffId, StaffWorkingHourRequest request) {
        validateHours(request);

        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Personel bulunamadı."));

        StaffWorkingHour workingHour = staffWorkingHourRepository
                .findByStaffIdAndDayOfWeek(staffId, request.getDayOfWeek())
                .orElseGet(() -> StaffWorkingHour.builder()
                        .staff(staff)
                        .dayOfWeek(request.getDayOfWeek())
                        .build());

        workingHour.setClosed(request.isClosed());
        workingHour.setOpenTime(request.isClosed() ? null : request.getOpenTime());
        workingHour.setCloseTime(request.isClosed() ? null : request.getCloseTime());

        return staffWorkingHourRepository.save(workingHour);
    }

    private void validateHours(StaffWorkingHourRequest request) {
        if (request.isClosed()) {
            return;
        }
        if (request.getOpenTime() == null || request.getCloseTime() == null) {
            throw new BusinessRuleException("Çalışılmayan bir gün için açılış ve kapanış saati zorunludur.");
        }
        if (!request.getOpenTime().isBefore(request.getCloseTime())) {
            throw new BusinessRuleException("Açılış saati kapanış saatinden önce olmalıdır.");
        }
    }
}
