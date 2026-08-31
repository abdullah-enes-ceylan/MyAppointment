package com.randevu.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Faz 3.1: gercek Postgres uzerinde calisan HER Spring context testinin
// (bkz. BackendApplicationTests, ilerideki HTTP/repository entegrasyon
// testleri) ortak temeli -- container acma/kapama ve Flyway migration'i
// tek yerde, kopyalanmadan.
//
// Flyway BURADA ELLE calistiriliyor, FlywayMigrationBootstrap (bkz. o
// sinif) KULLANILMIYOR: o sinif artik spring.factories ile her
// SpringApplication'a otomatik kayitli, ama spring.datasource.url'i bir
// Environment PROPERTY'si olarak, hicbir bean kurulmadan once ates lenen
// ApplicationEnvironmentPreparedEvent aninda okuyor. @ServiceConnection ise
// baglanti bilgisini bir ConnectionDetails BEAN'i olarak, cok daha GEC bir
// asamada (context initializer) sagliyor -- yani o listener bu senaryoda
// url'i asla goremez. Container'in JDBC bilgisi burada zaten (getJdbcUrl()
// ile) elde oldugu icin, Flyway'i context hic kurulmadan ONCE, dogrudan
// container'a karsi calistiriyoruz. Bu satirlar FlywayMigrationBootstrap'in
// mantigiyla kasitli olarak AYNI (DRY ihlali degil -- iki farkli
// baglanti-bilgisi kaynagini/zamanlamasini uzlastiriyorlar).
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
public abstract class AbstractIntegrationTest {

	// static + protected: alt siniflar container'a erismek isterse (ornegin
	// ileride bir entegrasyon testi container'in portunu/URL'ini okumak
	// isterse) dogrudan kullanabilir. TEK container tum alt siniflar
	// boyunca miras alinir -- her test sinifi kendi @BeforeAll'inda ayni
	// statik alanla calisir, JUnit/Testcontainers her sinif calistirmasinda
	// gerekirse yeniden baslatir. postgres:18-alpine: gelistiricinin yerel
	// kurulumuyla (Postgres 18.4) ayni majör versiyon.
	@Container
	@ServiceConnection
	protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@BeforeAll
	static void migrateSchema() {
		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration")
				.load()
				.migrate();
	}

}
