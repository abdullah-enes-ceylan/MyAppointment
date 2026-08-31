package com.randevu.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Faz 3.3: Swagger UI'daki "Authorize" kilidinin JWT bearer semasini
// tanimasi icin. Bu olmadan Swagger UI'dan korumali bir ucu denemek
// istediginizde Authorization header'ini elle eklemenin bir yolu olmazdi
// -- her istek icin token'i Postman/curl'e tasimaniz gerekirdi.
// SecurityRequirement GLOBAL uygulaniyor (tek "bearerAuth" semasi, tum
// uclara varsayilan olarak ekleniyor) cunku bu projede public/private uc
// ayrimi zaten SecurityConfig'te (permitAll listesi) yapiliyor -- Swagger
// UI'da hangi ucun gercekte kimlik istedigini, hangisinin istemedigini
// ayri ayri isaretlemek (@SecurityRequirement anotasyonuyla metot bazinda)
// bu adimin kapsamini asardi, dokumantasyonun asil amaci (uclarin var
// oldugunu ve semasini gormek) icin sart degil.
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI randevumOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Randevum API")
                        .description("Coklu isletme randevu ve yonetim sistemi -- backend API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
