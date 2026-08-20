package com.randevu.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// GECICI: Flyway (Faz 1.4) gelene kadar burada duruyor. O zaman bu index
// duzgun bir migration script'ine tasinacak ve bu sinif silinecek.
//
// AppointmentService.createAppointment "once kontrol et, sonra yaz"
// mantiginda calisiyor (exists sorgusu + save). Iki es zamanli istek ayni
// saate rezervasyon yapmaya calisirsa, ikisi de kontrolu HENUZ HICBIRI
// COMMIT ETMEMISKEN gecebilir ve iki kayit birden olusur — uygulama
// katmanindaki hicbir if/exists kontrolu bunu tek basina engelleyemez,
// tek gercek garanti veritabaninin kendisidir (bkz. ROADMAP K9).
//
// Neden PLAIN bir UNIQUE(business_id, appointment_date) degil de KISMI
// (partial/filtered) index: REJECTED/CANCELLED durumundaki bir randevu o
// saati "isgal etmis" sayilmamali — musteri reddedilen ya da iptal edilen
// bir saate tekrar randevu isteyebilmeli. Sadece PENDING/APPROVED
// durumundaki kayitlar gercekten "dolu" sayilir; index WHERE kosuluyla
// sadece onlari kapsiyor. Duz JPA @UniqueConstraint bunu ifade edemiyor
// (WHERE kosulu desteklemiyor), o yuzden native SQL gerekiyor.
@Component
public class AppointmentSlotIndexInitializer implements CommandLineRunner {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        entityManager.createNativeQuery(
                "CREATE UNIQUE INDEX IF NOT EXISTS ux_appointments_active_slot " +
                        "ON appointments (business_id, appointment_date) " +
                        "WHERE status IN ('PENDING', 'APPROVED')")
                .executeUpdate();
    }
}
