package com.randevu.backend.mapper;

import com.randevu.backend.dto.response.AppointmentResponse;
import com.randevu.backend.dto.response.BusinessSummary;
import com.randevu.backend.dto.response.CustomerSummary;
import com.randevu.backend.dto.response.StaffSummary;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.Staff;
import com.randevu.backend.entity.User;

import java.time.LocalDateTime;

public final class AppointmentMapper {

    private AppointmentMapper() {
    }

    // hasReview=false ile sabit -- cagiran taraf bu bilgiyi onemsemiyorsa
    // (bkz. asagidaki overload) her randevu icin ekstra bir ReviewRepository
    // sorgusu yapmaya gerek yok.
    public static AppointmentResponse toResponse(Appointment appointment) {
        return toResponse(appointment, false, null);
    }

    public static AppointmentResponse toResponse(Appointment appointment, boolean hasReview) {
        return toResponse(appointment, hasReview, null);
    }

    // Faz 2.10: hasReview BILEREK parametre -- bu sinif diger mapper'lar
    // gibi durumsuz kalmali, ReviewRepository'ye kendi erisip sorgu
    // atmamali (SRP, bkz. BusinessMapper'daki ayni gerekce). Puani
    // hesaplayip buraya veren taraf AppointmentController.getMyAppointments.
    // expiresAt de hasReview gibi BILEREK parametre: bu sinif durumsuz
    // kalmali, AppointmentExpiryPolicy'yi kendi icine enjekte etmemeli.
    // Hesabi yapip buraya veren taraf AppointmentController.
    public static AppointmentResponse toResponse(Appointment appointment, boolean hasReview,
            LocalDateTime expiresAt) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getAppointmentDate(),
                appointment.getStatus(),
                new BusinessSummary(appointment.getBusiness().getId(), appointment.getBusiness().getName()),
                ServiceItemMapper.toResponse(appointment.getServiceItem()),
                toCustomerSummary(appointment.getCustomer()),
                toStaffSummary(appointment.getStaff()),
                hasReview,
                expiresAt);
    }

    private static StaffSummary toStaffSummary(Staff staff) {
        return staff == null ? null : new StaffSummary(staff.getId(), staff.getName());
    }

    private static CustomerSummary toCustomerSummary(User customer) {
        return new CustomerSummary(customer.getId(), customer.getName(), customer.getSurName(), customer.getPhone());
    }
}
