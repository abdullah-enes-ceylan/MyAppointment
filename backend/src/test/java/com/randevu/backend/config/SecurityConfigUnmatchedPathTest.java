package com.randevu.backend.config;

import com.randevu.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Paket DIKKAT: klasik org.springframework.boot.test.autoconfigure.web.servlet
// DEGIL -- bu proje spring-boot-starter-webmvc-test (yeni, modulerize
// modul) kullaniyor, o da @AutoConfigureMockMvc'yi bu YENI paketten
// sagliyor (bkz. RestAuthenticationEntryPoint'teki ayni "webmvc, web
// degil" gerekcesi).
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig'in authorizeHttpRequests bloğunda BİLEREK hiç anyRequest()
// satırı yok -- sadece belirli path'ler icin kural var. Bu, eski Spring
// Security surumlerinde eslesmeyen path'lerin SESSIZCE korumasiz kalmasina
// yol acan bilinen bir tuzak. Faz 3.6 sirasinda bu projede GERCEKTEN boyle
// bir bosluk olup olmadigi canli test edildi: gecici, /api/** ile
// BASLAMAYAN bir uc eklenip kimlik dogrulamasiz istek atildi -- sonuc 401
// (RestAuthenticationEntryPoint'in kendi govdesiyle) cikti, yani bu
// Spring Security surumu eslesmeyen path'leri VARSAYILAN OLARAK reddediyor.
// Iddia edilen acik gercek degildi.
//
// Bu test o CANLI dogrulanmis davranisi KALICI hale getiriyor -- kod
// eklemek yerine (anyRequest().authenticated() gercekte hicbir sey
// DUZELTMEZ, sadece zaten var olan varsayilani belgeler ve test onu
// KANITLAMAZ), davranisin KENDISINI kilitliyoruz. Ileride Spring Security
// major surumu yukseltilir ya da SecurityConfig yeniden yazilirsa ve bu
// varsayilan degisirse, bu test kirilir ve haber verir.
@AutoConfigureMockMvc
class SecurityConfigUnmatchedPathTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hicbirGuvenlikKuraliylaEslesmeyenPath_sessizceIzinVerilmezVeReddedilir() throws Exception {
        // Rastgele bir path -- ne bir controller'da tanimli ne SecurityConfig'te
        // bir requestMatchers() kuralina giriyor. Guvenlik filtre zinciri
        // DispatcherServlet'ten (dolayisiyla "boyle bir controller yok" 404'unden)
        // ONCE calisir, bu yuzden gercek bir controller'a hic ihtiyac yok.
        String unmatchedPath = "/hicbir-kurala-girmeyen-yol-" + UUID.randomUUID();

        mockMvc.perform(get(unmatchedPath))
                .andExpect(status().isUnauthorized());
    }
}
