package com.randevu.backend.mapper;

import com.randevu.backend.dto.response.StaffResponse;
import com.randevu.backend.entity.Staff;

public final class StaffMapper {

    private StaffMapper() {
    }

    public static StaffResponse toResponse(Staff staff) {
        return new StaffResponse(
                staff.getId(),
                staff.getName(),
                staff.getServices().stream()
                        .map(ServiceItemMapper::toResponse)
                        .toList());
    }
}
