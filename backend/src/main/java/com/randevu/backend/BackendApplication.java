package com.randevu.backend;

import com.randevu.backend.config.FlywayMigrationBootstrap;
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

	public static void main(String[] args) {
		// FlywayMigrationBootstrap neden addListeners ile elle eklendi,
		// neden @Component değil: bkz. o sınıftaki açıklama.
		SpringApplication app = new SpringApplication(BackendApplication.class);
		app.addListeners(new FlywayMigrationBootstrap());
		app.run(args);
	}

}
