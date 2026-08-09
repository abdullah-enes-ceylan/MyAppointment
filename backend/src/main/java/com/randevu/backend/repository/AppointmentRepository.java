package com.randevu.backend.repository;

import java.util.List;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByBusinessId(Long businessId);

    List<Appointment> findByCustomerId(Long customerId);

    List<Appointment> findByBusinessIdAndStatus(Long businessId, AppointmentStatus status);

    List<Appointment> findByCustomerIdAndStatus(Long customerId, AppointmentStatus status);

    List<Appointment> findByCustomerIdAndAppointmentDateAfter(Long customerId, LocalDateTime appointmentDate);

    List<Appointment> findByBusinessIdAndAppointmentDateAfter(Long businessId, LocalDateTime appointmentDate);

    // Belirli bir dükkanda, belirli bir saatte ve belirli durumlarda randevu var mı
    // kontrolü
    boolean existsByBusinessIdAndAppointmentDateAndStatusIn(
            Long businessId,
            LocalDateTime appointmentDate,
            List<AppointmentStatus> statuses);

    List<Appointment> findByBusinessIdAndAppointmentDateBetweenAndStatusIn(
            Long businessId,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay,
            List<AppointmentStatus> statuses);

}
