package com.randevu.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: Faz 2.2'deki AppointmentCompletionScheduler'in
// @Scheduled metodunu calistirabilmesi icin gerekli -- bu anotasyon
// olmadan @Scheduled sessizce yok sayilir (hata da vermez), Spring hicbir
// arka plan gorevi tetiklemez.
@EnableScheduling
@SpringBootApplication
public class BackendApplication {

	// FlywayMigrationBootstrap burada ELLE eklenmiyor -- META-INF/spring.factories
	// uzerinden kayitli (bkz. o dosya ve FlywayMigrationBootstrap'taki
	// aciklama), boylece @SpringBootTest gibi bu main()'i hic cagirmayan
	// yollar da ayni erken Flyway calistirmasini goruyor.
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
