package com.randevu.backend;

import org.junit.jupiter.api.Test;

// Container acma/kapama ve Flyway migration'i AbstractIntegrationTest'te --
// bkz. o sinifin acikalamasi (ozellikle neden Flyway'in FlywayMigrationBootstrap
// yerine burada elle calistirildigi). Bu sinifin tek isi: context'in ve
// migration'larin GERCEKTEN tertemiz bir semada, hatasiz calistigini
// dogrulamak (eskiden gelistiricinin kendi kalici/native Postgres
// kurulumuna baglaniyordu -- gecerdi ama sadece o makinede).
class BackendApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
