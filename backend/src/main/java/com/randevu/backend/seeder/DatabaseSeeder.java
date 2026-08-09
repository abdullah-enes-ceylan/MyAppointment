package com.randevu.backend.seeder;

import com.randevu.backend.entity.*;
import com.randevu.backend.repository.*;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final AppointmentRepository appointmentRepository;

    public DatabaseSeeder(UserRepository userRepository,
            BusinessRepository businessRepository,
            ServiceItemRepository serviceItemRepository,
            AppointmentRepository appointmentRepository) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public void run(String... args) {

        // Veriler zaten varsa tekrar ekleme
        if (businessRepository.count() > 0) {
            return;
        }

        // ==========================
        // Kullanıcı
        // ==========================
        User customer = User.builder()
                .name("Enes")
                .surName("Yılmaz")
                .email("enes@test.com")
                .password("123456")
                .phone("05554443322")
                .role(Role.USER)
                .build();

        customer = userRepository.save(customer);

        // ==========================
        // İşletme
        // ==========================
        Business business = Business.builder()
                .name("Matrix Kuaför")
                .address("Konya / Selçuklu")
                .phone("05551112233")
                .description("Profesyonel erkek kuaförü")
                .owner(customer)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .build();

        business = businessRepository.save(business);

        // ==========================
        // Hizmetler
        // ==========================
        ServiceItem haircut = ServiceItem.builder()
                .name("Saç Kesimi")
                .description("Profesyonel saç kesimi")
                .price(300)
                .durationInMinutes(45)
                .business(business)
                .build();

        ServiceItem beardTrim = ServiceItem.builder()
                .name("Sakal Traşı")
                .description("Profesyonel sakal traşı")
                .price(150)
                .durationInMinutes(30)
                .business(business)
                .build();

        haircut = serviceItemRepository.save(haircut);
        beardTrim = serviceItemRepository.save(beardTrim);

        // ==========================
        // Randevular
        // ==========================
        Appointment appointment1 = Appointment.builder()
                .appointmentDate(LocalDateTime.of(2026, 8, 15, 9, 0))
                .status(AppointmentStatus.APPROVED)
                .customer(customer)
                .business(business)
                .serviceItem(haircut)
                .build();

        Appointment appointment2 = Appointment.builder()
                .appointmentDate(LocalDateTime.of(2026, 8, 15, 10, 30))
                .status(AppointmentStatus.PENDING)
                .customer(customer)
                .business(business)
                .serviceItem(beardTrim)
                .build();

        appointmentRepository.saveAll(Arrays.asList(appointment1, appointment2));

        System.out.println("✅ Database başarıyla tohumlandı.");
    }
}