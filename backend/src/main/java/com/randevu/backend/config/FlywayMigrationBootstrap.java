package com.randevu.backend.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;

// Bu projenin kullandığı Spring Boot sürümünde spring-boot-autoconfigure
// jar'ındaki FlywayAutoConfiguration hiç devreye girmiyor — --debug ile
// alınan condition evaluation raporunda Flyway'in adı bile geçmiyor
// (JPA/Hibernate gibi diğer parçalar org.springframework.boot.hibernate.
// autoconfigure gibi YENİ, modülerize edilmiş ayrı jar'lardan geliyor;
// Flyway'in bu yeni sisteme henüz taşınmamış olması muhtemel).
//
// Otomatiğin çalışmasını beklemek yerine Flyway'i ELLE çalıştırıyoruz —
// ama SIRADAN bir @Component/CommandLineRunner olarak DEĞİL. Sıradan bir
// bean, context tamamen kurulduktan SONRA çalışır — o noktada Hibernate
// zaten ddl-auto=validate kontrolünü yapıp şema yok diye patlamış olur.
// Bu yüzden ApplicationListener<ApplicationEnvironmentPreparedEvent>
// kullanıyoruz: bu, Environment hazır olur olmaz ama HİÇBİR @Bean
// (EntityManagerFactory dahil) kurulmadan ÖNCE tetiklenen erken bir kanca.
//
// Kayıt META-INF/spring.factories üzerinden (BackendApplication.main()'de
// elle DEĞİL) — bu sınıf ilk yazıldığında main()'de "addListeners(...)"
// ile eklenmişti, bu da SADECE main() üzerinden başlatılan bir
// SpringApplication'a bağlıydı. Gerçek bir boşluktu: @SpringBootTest kendi
// SpringApplication'ını kendisi kurduğu için main()'e hiç uğramıyor, o
// yüzden Flyway hiç çalışmadan Hibernate boş/eksik bir şemaya bakıp
// SESSİZCE geçebiliyordu (Faz 3.1'de Testcontainers'a geçilirken fark
// edildi). spring.factories kaydı bu listener'ı HANGİ yoldan başlatılırsa
// başlatılsın her SpringApplication'a otomatik ekliyor.
//
// NOT — Testcontainers + @ServiceConnection için bu YİNE de yetmez: o
// mekanizma bağlantı bilgisini bir bean (JdbcConnectionDetails) olarak,
// bu listener'ın tetiklendiği ApplicationEnvironmentPreparedEvent'ten
// SONRA (context initializer aşamasında) sağlıyor — yani env.getProperty
// ("spring.datasource.url") o an hâlâ null döner. Testcontainers tabanlı
// testler bu yüzden Flyway'i kendi başlarına (bkz. AbstractIntegrationTest)
// container'ın bilgisiyle elle çalıştırıyor; bu spring.factories kaydı
// sadece main()/dev/prod ile @DynamicPropertySource gibi PROPERTY tabanlı
// senaryoları kapsıyor. "test" profilinde url'in bulunamaması bu yüzden
// BİLEREK sessizce atlanıyor (asagidaki isTestProfile kontrolü) — aksi
// halde spring.factories kaydı eklendiginde AbstractIntegrationTest'in
// KENDİ, doğru migration'ı çalışmadan bu listener kendi (yanlış) fail-fast
// hatasını fırlatıp context'i hiç kurdurmuyordu (Faz 3.1'de canlı görüldü).
//
// KRİTİK ayrıntı — Ordered.LOWEST_PRECEDENCE şart: aynı olaya (
// ApplicationEnvironmentPreparedEvent) Spring'in KENDİ ortam yükleyicisi
// (application-{profil}.properties dosyalarını asıl OKUYAN mekanizma) de
// abone. Sıralama belirtilmeden bu iki dinleyici arasında bir YARIŞ oluyor
// — bazı çalıştırmalarda benim kodum önce çalışıp spring.datasource.url'i
// HENÜZ YÜKLENMEMİŞ buluyordu ("Missing required JDBC URL" hatası,
// rastgele/aralıklı sekilde). LOWEST_PRECEDENCE, bu listener'ın olabildiğince
// GEÇ çalışmasını, yani ortam tamamen hazır olduktan SONRA çalışmasını
// garanti ediyor.
public class FlywayMigrationBootstrap implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();

        String url = env.getProperty("spring.datasource.url");
        String username = env.getProperty("spring.datasource.username");
        String password = env.getProperty("spring.datasource.password");

        if (url == null || url.isBlank()) {
            // "test" profilinde url'in boş olması BEKLENEN bir durum:
            // Testcontainers + @ServiceConnection bağlantı bilgisini bu
            // noktadan SONRA (bean aşamasında) sağlıyor, migration'ı da
            // AbstractIntegrationTest zaten kendi başına, container'ın
            // gerçek bilgisiyle çalıştırıyor (bkz. yukarıdaki NOT). Burada
            // fail-fast'e düşmek, o doğru mekanizmanın hiç çalışma şansı
            // bulamadan context kurulumunu tamamen durdururdu.
            if (Arrays.asList(env.getActiveProfiles()).contains("test")) {
                return;
            }
            // Diğer her profilde (dev/prod) bu, sessizce yanlış/eksik bir
            // yapılandırmayla devam etmek yerine ne olduğu açık bir hatayla
            // aciliste durmalı (fail-fast) — "Missing required JDBC URL"
            // gibi Flyway'in kendi genel hatasını görüp neden oluştuğunu
            // tahmin etmeye çalışmaktansa.
            throw new FlywayException(
                    "spring.datasource.url ortam hazirlanirken cozulemedi. "
                    + "FlywayMigrationBootstrap'in siralamasi (Ordered.LOWEST_PRECEDENCE) "
                    + "kontrol edilmeli.");
        }

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
