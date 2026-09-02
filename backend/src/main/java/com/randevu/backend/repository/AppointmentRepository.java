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

    List<Appointment> findByStatus(AppointmentStatus status);

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

    // Faz 2.5: staffId dolu bir randevunun cakisma kontrolu artik personel
    // bazli -- yukaridaki business bazli sorgularla ayni amaca hizmet
    // ediyor, sadece filtre business_id yerine staff_id.
    boolean existsByStaffIdAndAppointmentDateAndStatusIn(
            Long staffId,
            LocalDateTime appointmentDate,
            List<AppointmentStatus> statuses);

    List<Appointment> findByStaffIdAndAppointmentDateBetweenAndStatusIn(
            Long staffId,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay,
            List<AppointmentStatus> statuses);

    // Profil ekranindaki ozet sayilar icin. Listeyi cekip size() almak
    // yerine COUNT sorgusu -- satirlarin kendisi hic lazim degil, sadece
    // adedi (100 randevusu olan bir kullanicida 100 satiri Java'ya tasiyip
    // atmanin anlami yok).
    long countByCustomerId(Long customerId);

    long countByCustomerIdAndStatus(Long customerId, AppointmentStatus status);

    // Acik talep sinirinin sayimi: bir musterinin AYNI ISLETMEDEKI
    // cevaplanmamis talepleri (bkz. AppointmentService.createAppointment).
    int countByCustomerIdAndBusinessIdAndStatus(Long customerId, Long businessId, AppointmentStatus status);

    long countByCustomerIdAndAppointmentDateAfterAndStatusIn(
            Long customerId,
            LocalDateTime after,
            List<AppointmentStatus> statuses);

    // --- Hesap silme akisi (Faz 3.9) ---

    // Musteri hesap silme talep ettiginde, gracePeriod sonunda kendi
    // (musteri tarafi) cevaplanmamis/onaylanmis randevularini iptal etmek
    // icin (bkz. AccountDeletionScheduler).
    List<Appointment> findByCustomerIdAndStatusIn(Long customerId, List<AppointmentStatus> statuses);

    // BUSINESS_OWNER silme talep ettigi ANDA, businessNoticePeriod
    // icindeki randevulari hemen iptal etmek icin (haber verme payi, bkz.
    // AccountDeletionService).
    List<Appointment> findByBusinessIdInAndStatusInAndAppointmentDateBefore(
            List<Long> businessIds,
            List<AppointmentStatus> statuses,
            LocalDateTime before);

    // businessReversalWindow gecip talep geri alinmadiysa, KALAN TUM
    // randevulari topluca iptal etmek icin (bkz. AccountDeletionScheduler).
    List<Appointment> findByBusinessIdInAndStatusIn(List<Long> businessIds, List<AppointmentStatus> statuses);

}
