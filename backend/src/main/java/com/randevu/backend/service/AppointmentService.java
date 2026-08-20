package com.randevu.backend.service;

import com.randevu.backend.entity.*;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BusinessRepository businessRepository;
    private final ServiceItemRepository serviceItemRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
            BusinessRepository businessRepository,
            ServiceItemRepository serviceItemRepository) {
        this.appointmentRepository = appointmentRepository;
        this.businessRepository = businessRepository;
        this.serviceItemRepository = serviceItemRepository;
    }

    // Yeni randevu oluşturur ve saat çakışmalarını kontrol eder.
    public Appointment createAppointment(Appointment newAppointment) {

        // Eskiden burada businessId hiç doğrulanmıyordu — controller sadece
        // "new Business(); setId(...)" ile boş bir stub kuruyordu. Olmayan
        // bir businessId, hizmet ile işletmenin eşleşmediği hallere kadar
        // gitmeden önce, ilk elden gerçek bir Business yükleyip var olduğunu
        // doğruluyoruz.
        Long businessId = newAppointment.getBusiness().getId();
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        ServiceItem service = serviceItemRepository.findById(newAppointment.getServiceItem().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hizmet bulunamadı."));

        // Kritik doğrulama: secilen hizmet gercekten bu isletmeye mi ait?
        // Bu kontrol olmadan, businessId=1 ve serviceId=999 (baska bir
        // isletmenin hizmeti) gonderilerek randevu olusturulabiliyordu —
        // yanlis sureyle cakisma hesabi yapiliyor, yanlis isletmenin
        // inbox'inda yanlis fiyat/hizmet gorunuyordu.
        if (!service.getBusiness().getId().equals(businessId)) {
            throw new BusinessRuleException("Seçilen hizmet bu işletmeye ait değil.");
        }

        // Soft delete edilmiş (artık sunulmayan) bir hizmete randevu alınamaz.
        if (!service.isActive()) {
            throw new ResourceNotFoundException("Hizmet bulunamadı.");
        }

        newAppointment.setBusiness(business);
        newAppointment.setServiceItem(service);

        // Spam tıklama koruması: Aynı dükkan + aynı saat için zaten istek varsa engelle
        List<AppointmentStatus> blockingStatuses = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);
        boolean alreadyExists = appointmentRepository.existsByBusinessIdAndAppointmentDateAndStatusIn(
                businessId,
                newAppointment.getAppointmentDate(),
                blockingStatuses);
        if (alreadyExists) {
            throw new BusinessRuleException("Bu saat için zaten bir randevu isteği mevcut!");
        }

        LocalDateTime newStart = newAppointment.getAppointmentDate();
        LocalDateTime newEnd = newStart.plusMinutes(service.getDurationInMinutes());

        LocalDateTime startOfDay = newStart.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        List<Appointment> dailyAppointments = appointmentRepository
                .findByBusinessIdAndAppointmentDateBetweenAndStatusIn(
                        businessId,
                        startOfDay,
                        endOfDay,
                        blockingStatuses);

        boolean isOverlapping = dailyAppointments.stream().anyMatch(existing -> {
            LocalDateTime existingStart = existing.getAppointmentDate();
            LocalDateTime existingEnd = existingStart.plusMinutes(existing.getServiceItem().getDurationInMinutes());

            return newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart);
        });

        if (isOverlapping) {
            throw new BusinessRuleException("Seçilen saat aralığında başka bir randevu bulunmaktadır.");
        }

        return appointmentRepository.save(newAppointment);
    }

    // İşletmenin onay bekleyen (PENDING) randevularını getirir — İstek Kutusu (Inbox).
    public List<Appointment> getPendingAppointmentsForBusiness(Long businessId) {
        return appointmentRepository.findByBusinessIdAndStatus(businessId, AppointmentStatus.PENDING);
    }

    // Belirli bir işletmeye ait tüm randevuları getirir.
    public List<Appointment> getBusinessAppointments(Long businessId) {
        return appointmentRepository.findByBusinessId(businessId);
    }

    // Belirli bir müşteriye ait tüm randevuları getirir.
    public List<Appointment> getCustomerAppointments(Long customerId) {
        return appointmentRepository.findByCustomerId(customerId);
    }

    // Randevunun durumunu günceller (Örn: PENDING -> APPROVED).
    public Appointment updateAppointmentStatus(Long appointmentId, AppointmentStatus newStatus) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Randevu bulunamadı."));

        appointment.setStatus(newStatus);
        return appointmentRepository.save(appointment);
    }

    // Müşterinin şu andan sonraki randevularını getirir.
    public List<Appointment> getUpcomingCustomerAppointments(Long customerId) {
        return appointmentRepository.findByCustomerIdAndAppointmentDateAfter(customerId, LocalDateTime.now());
    }

    // İşletmenin şu andan sonraki randevularını getirir.
    public List<Appointment> getUpcomingBusinessAppointments(Long businessId) {
        return appointmentRepository.findByBusinessIdAndAppointmentDateAfter(businessId, LocalDateTime.now());
    }

    // Belirtilen gün için işletmenin ve hizmetin süresine uygun boş saat
    // dilimlerini hesaplar.
    public List<LocalTime> getAvailableTimeSlots(Long businessId, Long serviceId, LocalDate date) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Dükkan bulunamadı."));

        ServiceItem serviceItem = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Hizmet bulunamadı."));

        int duration = serviceItem.getDurationInMinutes();

        LocalDateTime startOfDay = date.atTime(business.getOpenTime());
        LocalDateTime endOfDay = date.atTime(business.getCloseTime());

        List<AppointmentStatus> blockingStatuses = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);
        List<Appointment> dailyAppointments = appointmentRepository
                .findByBusinessIdAndAppointmentDateBetweenAndStatusIn(businessId, startOfDay, endOfDay,
                        blockingStatuses);

        dailyAppointments.sort(Comparator.nullsLast(Comparator.comparing(a -> a.getAppointmentDate())));
        List<LocalTime> availableSlots = new ArrayList<>();
        LocalDateTime currentPointer = startOfDay;

        while (currentPointer.plusMinutes(duration).isBefore(endOfDay)
                || currentPointer.plusMinutes(duration).isEqual(endOfDay)) {

            LocalDateTime proposedEnd = currentPointer.plusMinutes(duration);
            boolean isOverlapping = false;

            for (Appointment app : dailyAppointments) {
                LocalDateTime appStart = app.getAppointmentDate();
                LocalDateTime appEnd = appStart.plusMinutes(app.getServiceItem().getDurationInMinutes());

                if (currentPointer.isBefore(appEnd) && proposedEnd.isAfter(appStart)) {
                    isOverlapping = true;
                    // Çakışma durumunda işaretçiyi mevcut randevunun bitiş zamanına kaydırır.
                    currentPointer = appEnd;
                    break;
                }
            }

            if (!isOverlapping) {
                // Uygun boşluk bulunduğunda listeye ekler ve işaretçiyi hizmet süresi kadar
                // ileri taşır.
                availableSlots.add(currentPointer.toLocalTime());
                currentPointer = currentPointer.plusMinutes(duration);
            }
        }

        return availableSlots;
    }
}
