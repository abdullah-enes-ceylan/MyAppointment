# syntax=docker/dockerfile:1

# --- Build asamasi ---
# Maven + tam JDK sadece burada var; final image'a hic gecmiyor. Once SADECE
# pom.xml kopyalanip bagimliliklar indiriliyor -- kaynak kod degistiginde bu
# katman Docker cache'inde kalir, her degisiklikte butun internet'i tekrar
# indirmeyiz.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
# Testler BILEREK burada calismiyor: Testcontainers gercek Docker'a ihtiyac
# duyuyor, build asamasinin kendisi bir konteyner icinde calisirken
# Docker-in-Docker'a girmek gereksiz karmasiklik. Testler zaten ayri
# calistiriliyor (mvn test) -- image build'i sadece paketliyor.
RUN mvn clean package -DskipTests -B

# --- Katman cikarma asamasi ---
# Spring Boot'un jarmode=tools araci, tek parca "fat jar"i Docker katmanlarina
# ayirir: dependencies (nadiren degisir), spring-boot-loader, snapshot-
# dependencies, application (her kod degisikliginde degisen tek katman budur).
# Boylece "application" disindaki katmanlar image push/pull'da tekrar tekrar
# aktarilmaz.
FROM eclipse-temurin:21-jre-jammy AS extract
WORKDIR /extract
COPY --from=build /build/target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

# --- Calistirma asamasi ---
# jammy (Ubuntu/glibc) SECILDI, alpine DEGIL: BusinessPhotoService Thumbnailator
# uzerinden ImageIO ile JPEG/PNG yeniden kodluyor; ImageIO'nun native kod
# cozucileri Alpine'in musl libc'siyle tarihte gercek uyumsuzluk yasadi. Foto
# yukleme bu urunun gercek bir ozelligi -- sessizce sadece Alpine'de bozulan
# bir upload'u yakalamak zor olurdu. Boyut farki (jammy ~200MB, alpine ~90MB)
# bu asamada (tek sunucu, beta) bu riske deger degil.
FROM eclipse-temurin:21-jre-jammy

# Root olmayan kullanici -- container ele gecirilirse (ornegin bir bagimlilikta
# RCE) saldirgan root yetkisi kazanmaz. Sabit UID/GID (1000): host'taki bind
# mount klasorunun sahipligini ONCEDEN, container'i hic calistirmadan ayni
# UID'ye gore ayarlayabilelim diye (bkz. docker-compose.yml, business-photo-
# storage volume gerekcesi).
RUN groupadd -r -g 1000 appgroup && useradd -r -u 1000 -g appgroup appuser

WORKDIR /app
COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./

# appuser'in jar'i calistirabilmesi ve (varsa) kendi yazdigi dosyalari
# okuyabilmesi icin /app appuser sahipliginde olmali.
#
# ONCEDEN burada /app/business-photo-storage icin ayrica bir "mkdir -p" da
# vardi (isletme fotograflari icin bir bind/named volume mount noktasi
# hazirlamak amacli) -- Faz 3.15'te depolama R2'ye tasinip prod'da
# storage-provider SABIT "r2" olunca (bkz. docker-compose.yml, NOTLAR.md
# "R2'ye tasima" karari) backend container'in yerel diskine hic yazmiyor,
# o dizine gerek kalmadi.
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# Caddy'nin depends_on: condition: service_healthy ile backend'i GERCEKTEN
# hazir olana kadar beklemesi icin (bkz. docker-compose.yml) -- ayni mantik
# postgres'in kendi healthcheck'inde de var. /actuator/health zaten
# kimlik dogrulamasiz erisilebilir (bkz. SecurityConfig).
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
