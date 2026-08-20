package com.randevu.backend;

import com.randevu.backend.config.FlywayMigrationBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
