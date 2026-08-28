package com.randevu.backend.service;

import com.randevu.backend.dto.response.ProfileStatsResponse;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.repository.AppointmentRepository;
import com.randevu.backend.repository.FavoriteRepository;
import com.randevu.backend.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.time.Clock;

// Profil ekranindaki ozet sayilari toplar. UserService'e DEGIL ayri bir
// sinifa konuldu (SRP): UserService "hesap yonetimi" (kayit, profil,
// sifre) isini yapiyor ve sadece userRepository + passwordEncoder'i
// taniyor. Randevu/favori/yorum sayimi tamamen baska bir sorumluluk --
// oraya koysaydik UserService uc ayri repository'ye daha bagimli hale
// gelir, hesap yonetimiyle ilgisi olmayan degisiklikler yuzunden
// degismek zorunda kalirdi.
@Service
public class ProfileStatsService {

    // "Yaklasan randevu" = tarihi gelecekte OLAN ve hala aktif olan.
    // Iptal/ret edilmis bir randevu tarihi gelecekte olsa bile yaklasan
    // sayilmaz -- AppointmentService'teki "engelleyici durumlar" listesiyle
    // ayni mantik.
    private static final List<AppointmentStatus> ACTIVE_STATUSES = List.of(
            AppointmentStatus.PENDING, AppointmentStatus.APPROVED);

    private final AppointmentRepository appointmentRepository;
    private final FavoriteRepository favoriteRepository;
    private final ReviewRepository reviewRepository;
    private final Clock clock;

    public ProfileStatsService(AppointmentRepository appointmentRepository,
            FavoriteRepository favoriteRepository,
            ReviewRepository reviewRepository,
            Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.favoriteRepository = favoriteRepository;
        this.reviewRepository = reviewRepository;
        this.clock = clock;
    }

    // Verilen kullanicinin profil ozet sayilarini hesaplar.
    public ProfileStatsResponse getStatsForUser(Long userId) {
        return new ProfileStatsResponse(
                appointmentRepository.countByCustomerId(userId),
                appointmentRepository.countByCustomerIdAndStatus(userId, AppointmentStatus.COMPLETED),
                appointmentRepository.countByCustomerIdAndAppointmentDateAfterAndStatusIn(
                        userId, LocalDateTime.now(clock), ACTIVE_STATUSES),
                favoriteRepository.countByUser_Id(userId),
                reviewRepository.countByAppointment_Customer_Id(userId));
    }
}
