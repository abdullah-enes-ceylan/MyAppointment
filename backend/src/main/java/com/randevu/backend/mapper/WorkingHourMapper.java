package com.randevu.backend.mapper;

import com.randevu.backend.dto.response.BusinessClosureResponse;
import com.randevu.backend.dto.response.WorkingHourResponse;
import com.randevu.backend.entity.BusinessClosure;
import com.randevu.backend.entity.WorkingHour;

public final class WorkingHourMapper {

    private WorkingHourMapper() {
    }

    public static WorkingHourResponse toResponse(WorkingHour workingHour) {
        return new WorkingHourResponse(
                workingHour.getDayOfWeek(),
                workingHour.getOpenTime(),
                workingHour.getCloseTime(),
                workingHour.isClosed());
    }

    public static BusinessClosureResponse toResponse(BusinessClosure closure) {
        return new BusinessClosureResponse(closure.getId(), closure.getDate(), closure.getReason());
    }
}
