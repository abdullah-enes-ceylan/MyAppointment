package com.randevu.backend.service;

import com.randevu.backend.dto.request.BusinessClosureRequest;
import com.randevu.backend.dto.request.WorkingHourRequest;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessClosure;
import com.randevu.backend.entity.WorkingHour;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.BusinessClosureRepository;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.WorkingHourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkingHourService {

    private final WorkingHourRepository workingHourRepository;
    private final BusinessClosureRepository businessClosureRepository;
    private final BusinessRepository businessRepository;

    public WorkingHourService(WorkingHourRepository workingHourRepository,
            BusinessClosureRepository businessClosureRepository,
            BusinessRepository businessRepository) {
        this.workingHourRepository = workingHourRepository;
        this.businessClosureRepository = businessClosureRepository;
        this.businessRepository = businessRepository;
    }

    public List<WorkingHour> getWorkingHours(Long businessId) {
        return workingHourRepository.findByBusinessId(businessId);
    }

    // Bir günün çalışma saatini ayarlar. O gün için zaten bir kayıt varsa
    // GÜNCELLER (upsert) — işletme sahibinin aynı güne iki kere farklı
    // saat girip yinelenen kayıt oluşturmasını engelliyor (bu, DB'deki
    // UNIQUE(business_id, day_of_week) kısıtıyla da garanti altında —
    // bkz. V2 migration).
    @Transactional
    public WorkingHour setWorkingHour(Long businessId, WorkingHourRequest request) {
        validateHours(request);

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        WorkingHour workingHour = workingHourRepository
                .findByBusinessIdAndDayOfWeek(businessId, request.getDayOfWeek())
                .orElseGet(() -> WorkingHour.builder()
                        .business(business)
                        .dayOfWeek(request.getDayOfWeek())
                        .build());

        workingHour.setClosed(request.isClosed());
        workingHour.setOpenTime(request.isClosed() ? null : request.getOpenTime());
        workingHour.setCloseTime(request.isClosed() ? null : request.getCloseTime());

        return workingHourRepository.save(workingHour);
    }

    // closed=false iken openTime/closeTime'ın dolu ve tutarlı olmasını
    // garantiler. Bean Validation tek başına "alanlar arası" (openTime <
    // closeTime, closed=false iken ikisi de zorunlu) kuralları temiz
    // ifade edemediği için burada, servis katmanında kontrol ediliyor.
    private void validateHours(WorkingHourRequest request) {
        if (request.isClosed()) {
            return;
        }
        if (request.getOpenTime() == null || request.getCloseTime() == null) {
            throw new BusinessRuleException("Kapalı olmayan bir gün için açılış ve kapanış saati zorunludur.");
        }
        if (!request.getOpenTime().isBefore(request.getCloseTime())) {
            throw new BusinessRuleException("Açılış saati kapanış saatinden önce olmalıdır.");
        }
    }

    public List<BusinessClosure> getClosures(Long businessId) {
        return businessClosureRepository.findByBusinessId(businessId);
    }

    @Transactional
    public BusinessClosure addClosure(Long businessId, BusinessClosureRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        businessClosureRepository.findByBusinessIdAndDate(businessId, request.getDate())
                .ifPresent(existing -> {
                    throw new BusinessRuleException("Bu tarih için zaten bir kapanış kaydı var.");
                });

        BusinessClosure closure = BusinessClosure.builder()
                .business(business)
                .date(request.getDate())
                .reason(request.getReason())
                .build();
        return businessClosureRepository.save(closure);
    }

    // Kapanış kaydının GERÇEKTEN bu businessId'ye ait olduğunu doğruluyor —
    // aksi halde bir işletme sahibi kendi businessId'siyle, başka bir
    // işletmenin closureId'sini tahmin ederek silebilirdi.
    @Transactional
    public void removeClosure(Long businessId, Long closureId) {
        BusinessClosure closure = businessClosureRepository.findById(closureId)
                .orElseThrow(() -> new ResourceNotFoundException("Kapanış kaydı bulunamadı."));
        if (!closure.getBusiness().getId().equals(businessId)) {
            throw new ResourceNotFoundException("Kapanış kaydı bulunamadı.");
        }
        businessClosureRepository.delete(closure);
    }
}
