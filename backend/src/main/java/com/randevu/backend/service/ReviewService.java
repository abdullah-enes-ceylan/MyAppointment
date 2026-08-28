package com.randevu.backend.service;

import com.randevu.backend.dto.request.ReviewRequest;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.entity.Review;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.AppointmentRepository;
import com.randevu.backend.repository.ReviewRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.time.Clock;

// "Sadece gerçekten gitmiş kişi yorum yapabilir" garantisinin SERVİS
// katmanı -- Review entity'sindeki UNIQUE(appointment_id) kısıtı (şema
// katmanı) TEK BAŞINA "kim, ne zaman, hangi durumdaki randevuya" yorum
// yapabileceğini ifade edemez, o yüzden burada. Üçüncü katman (sunum,
// "yorum butonu sadece COMPLETED randevularda görünür") frontend'de --
// bkz. ROADMAP 2.6: o katman SADECE UX, güvenlik değil, asıl garanti
// burada ve DB'de.
@Service
public class ReviewService {

    // appointmentDate cok eski bir tamamlanmis randevu icin sonsuza kadar
    // yorum kabul etmenin anlami yok (ornegin 2 yil once tamamlanmis bir
    // randevuya bugun "yorum" eklemek ne musteri ne isletme icin anlamli).
    private static final int REVIEW_WINDOW_DAYS = 30;

    private final ReviewRepository reviewRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public ReviewService(ReviewRepository reviewRepository, AppointmentRepository appointmentRepository,
            Clock clock) {
        this.reviewRepository = reviewRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Transactional
    public Review createReview(Long currentUserId, ReviewRequest request) {
        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Randevu bulunamadı."));

        // 1) Baskasinin randevusuna yorum yazamaz -- kimlik path/body'den
        // degil appointment uzerinden (dolayli token) dogrulaniyor.
        if (!appointment.getCustomer().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Bu randevuya yorum yapma yetkiniz yok.");
        }

        // 2) Sadece GERCEKTEN gerceklesmis randevular -- PENDING/REJECTED/
        // CANCELLED/NO_SHOW asla yorum alamaz. NO_SHOW'un burada elenmesi
        // ozellikle onemli: musteri gelmediyse deneyim hic yasanmadi.
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException("Sadece tamamlanmış randevulara yorum yapılabilir.");
        }

        // 3) Makul zaman penceresi. Not: COMPLETED durumuna SADECE randevu
        // saati + hizmet suresi gecince (Faz 2.2'deki job ile) ulasilabiliyor,
        // yani appointmentDate'in gecmiste oldugu zaten garanti -- burada
        // ayrica "gecmiste mi" diye bakmaya gerek yok, sadece "cok mu eski"
        // kontrol ediliyor.
        if (appointment.getAppointmentDate().isBefore(LocalDateTime.now(clock).minusDays(REVIEW_WINDOW_DAYS))) {
            throw new BusinessRuleException(
                    "Bu randevu için yorum yapma süresi doldu (" + REVIEW_WINDOW_DAYS + " gün).");
        }

        // 4) Bir randevuya iki yorum yazilamaz. Bu kontrol DB'deki
        // UNIQUE(appointment_id)'nin ON'DEN, kullaniciya anlamli bir mesaj
        // vermek icin yapiliyor -- asil garanti yine de DB'de (bu kontrol
        // atlansa/bozulsa bile save() DataIntegrityViolationException
        // firlatir, GlobalExceptionHandler 409'a cevirir).
        reviewRepository.findByAppointmentId(appointment.getId()).ifPresent(existing -> {
            throw new BusinessRuleException("Bu randevu için zaten bir yorum yapılmış.");
        });

        Review review = Review.builder()
                .appointment(appointment)
                .rating(request.getRating())
                .comment(request.getComment())
                .createdAt(LocalDateTime.now(clock))
                .build();

        return reviewRepository.save(review);
    }

    public List<Review> getReviewsForBusiness(Long businessId) {
        return reviewRepository.findByAppointment_Business_IdOrderByCreatedAtDesc(businessId);
    }

    // Faz 2.10: musterinin "Randevularim" ekraninda "Yorum Yap" butonunun
    // hangi randevularda gosterilecegini belirlemek icin -- zaten yorumu
    // olan bir randevuda buton hic gorunmemeli (aksi halde tiklayinca
    // createReview'daki 4. kontrolden 409 donerdi, kotu kullanici deneyimi).
    public boolean hasReview(Long appointmentId) {
        return reviewRepository.findByAppointmentId(appointmentId).isPresent();
    }
}
