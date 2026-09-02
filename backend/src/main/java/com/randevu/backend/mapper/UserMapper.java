package com.randevu.backend.mapper;

import com.randevu.backend.dto.response.UserResponse;
import com.randevu.backend.entity.User;

import java.time.LocalDateTime;

public final class UserMapper {

    private UserMapper() {
    }

    // Silme eşiklerini HİÇ bilmeyen çağıranlar için (register, admin listesi,
    // profil güncelleme sonrası) -- eşikler daima null döner. Bu sınıf
    // AppointmentMapper'daki hasReview/expiresAt İLE AYNI desende durumsuz
    // kalıyor: AccountDeletionProperties'e/Clock'a kendi başına erişmiyor,
    // hesabı yapıp buraya veren taraf UserController.getMyProfile.
    public static UserResponse toResponse(User user) {
        return toResponse(user, null, null);
    }

    public static UserResponse toResponse(User user, LocalDateTime identityAnonymizationDeadlineAt,
            LocalDateTime businessReversalDeadlineAt) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getSurName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getDeletionRequestedAt(),
                identityAnonymizationDeadlineAt,
                businessReversalDeadlineAt);
    }
}
