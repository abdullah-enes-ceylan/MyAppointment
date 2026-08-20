package com.randevu.backend.mapper;

import com.randevu.backend.dto.response.UserResponse;
import com.randevu.backend.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getSurName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole());
    }
}
