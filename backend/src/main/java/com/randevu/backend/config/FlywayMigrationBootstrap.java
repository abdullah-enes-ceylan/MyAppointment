package com.randevu.backend.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

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
// kullanıyoruz: bu, Environment (profiller, property dosyaları) hazır
// olur olmaz ama HİÇBİR @Bean (EntityManagerFactory dahil) kurulmadan
// ÖNCE tetiklenen, en erken güvenilir kanca. main()'de SpringApplication.
// addListeners(...) ile elle kaydediliyor çünkü component-scanning'in
// kendisi de context refresh sırasında olur — o zaman zaten çok geç kalır.
public class FlywayMigrationBootstrap implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();

        String url = env.getProperty("spring.datasource.url");
        String username = env.getProperty("spring.datasource.username");
        String password = env.getProperty("spring.datasource.password");

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
