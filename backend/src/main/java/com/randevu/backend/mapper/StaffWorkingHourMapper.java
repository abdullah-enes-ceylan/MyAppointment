package com.randevu.backend.mapper;

import com.randevu.backend.dto.response.StaffWorkingHourResponse;
import com.randevu.backend.entity.StaffWorkingHour;

public final class StaffWorkingHourMapper {

    private StaffWorkingHourMapper() {
    }

    public static StaffWorkingHourResponse toResponse(StaffWorkingHour workingHour) {
        return new StaffWorkingHourResponse(
                workingHour.getDayOfWeek(),
                workingHour.getOpenTime(),
                workingHour.getCloseTime(),
                workingHour.isClosed());
    }
}
