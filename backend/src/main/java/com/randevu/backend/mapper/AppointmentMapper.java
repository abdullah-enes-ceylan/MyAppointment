package com.randevu.backend.mapper;

import com.randevu.backend.dto.response.AppointmentResponse;
import com.randevu.backend.dto.response.BusinessSummary;
import com.randevu.backend.dto.response.CustomerSummary;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.User;

public final class AppointmentMapper {

    private AppointmentMapper() {
    }

    public static AppointmentResponse toResponse(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getAppointmentDate(),
                appointment.getStatus(),
                new BusinessSummary(appointment.getBusiness().getId(), appointment.getBusiness().getName()),
                ServiceItemMapper.toResponse(appointment.getServiceItem()),
                toCustomerSummary(appointment.getCustomer()));
    }

    private static CustomerSummary toCustomerSummary(User customer) {
        return new CustomerSummary(customer.getId(), customer.getName(), customer.getSurName(), customer.getPhone());
    }
}
