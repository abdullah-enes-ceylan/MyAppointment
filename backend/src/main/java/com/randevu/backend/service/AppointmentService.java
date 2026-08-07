package com.randevu.backend.service;

import com.randevu.backend.entity.*;
import com.randevu.backend.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final ServiceItemRepository serviceItemRepository;

    // Bütün Repository'leri içeri alıyoruz (Dependency Injection)
    public AppointmentService(AppointmentRepository appointmentRepository,
            UserRepository userRepository,
            BusinessRepository businessRepository,
            ServiceItemRepository serviceItemRepository) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.serviceItemRepository = serviceItemRepository;
    }

    // 1. YENİ RANDEVU OLUŞTURMA
    public Appointment createAppointment(Long customerId, Long businessId, Long serviceId,
            LocalDateTime appointmentDate) {

        // 1. Adım: Müşteri veritabanında var mı?
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Müşteri bulunamadı!"));

        // 2. Adım: Dükkan veritabanında var mı?
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Dükkan bulunamadı!"));

        // 3. Adım: Hizmet veritabanında var mı?
        ServiceItem serviceItem = serviceItemRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Hizmet bulunamadı!"));

        // Hangi durumlar o saatin dolu olduğunu gösterir? (Bekleyen ve Onaylananlar)
        List<AppointmentStatus> blockingStatuses = List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED);

        // Veritabanına sor: Bu dükkanda, bu saatte, bu durumlardan birine sahip kayıt
        // var mı?
        boolean isTimeSlotTaken = appointmentRepository.existsByBusinessIdAndAppointmentDateAndStatusIn(
                businessId, appointmentDate, blockingStatuses);

        if (isTimeSlotTaken) {
            throw new RuntimeException("Bu saatte dükkanın başka bir randevusu var, lütfen farklı bir saat seçiniz!");
        }

        // 4. Adım: Her şey tamsa randevuyu oluştur (Lombok Builder kullanarak)
        Appointment appointment = Appointment.builder()
                .customer(customer)
                .business(business)
                .serviceItem(serviceItem)
                .appointmentDate(appointmentDate)
                .status(AppointmentStatus.PENDING) // İlk aşamada otomatik olarak 'Onay Bekliyor' yapıyoruz
                .build();

        return appointmentRepository.save(appointment);
    }

    // 2. DÜKKANA AİT RANDEVULARI LİSTELEME
    public List<Appointment> getBusinessAppointments(Long businessId) {
        return appointmentRepository.findByBusinessId(businessId);
    }

    // 3. MÜŞTERİYE AİT RANDEVULARI LİSTELEME
    public List<Appointment> getCustomerAppointments(Long customerId) {
        return appointmentRepository.findByCustomerId(customerId);
    }

    // 4. RANDEVU DURUMUNU GÜNCELLEME (Patronun onaylaması veya iptal etmesi için)
    public Appointment updateAppointmentStatus(Long appointmentId, AppointmentStatus newStatus) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Randevu bulunamadı!"));

        appointment.setStatus(newStatus);
        return appointmentRepository.save(appointment);
    }

    // 5. Müşterinin Gelecekteki Randevuları (Geçmişe bakmaz)
    public List<Appointment> getUpcomingCustomerAppointments(Long customerId) {
        return appointmentRepository.findByCustomerIdAndAppointmentDateAfter(customerId, LocalDateTime.now());
    }

    // 6. Dükkanın Gelecekteki Randevuları
    public List<Appointment> getUpcomingBusinessAppointments(Long businessId) {
        return appointmentRepository.findByBusinessIdAndAppointmentDateAfter(businessId, LocalDateTime.now());
    }

}