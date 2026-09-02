package com.randevu.backend.config;

import com.randevu.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Faz 3.9 -- SecurityConfig'ten "/api/businesses/*/working-hours" ve
// ".../closures" icin permitAll satirlari kaldirildi, "/api/service-items/
// business/{id}" zaten authenticated'ti ama sahiplik kontrolu yoktu. Bu
// test SADECE SecurityConfig'teki degisikligin GERCEKTEN etkili oldugunu
// (gercek filtre zincirinden gecerek, controller birim testlerinin
// KAPSAYAMADIGI seviyede) kanitliyor -- kimliksiz bir istek artik bu
// uclarin hicbirinden veri alamiyor. Sahiplik mantiginin kendisi (sahip
// vs saldirgan) WorkingHourControllerOwnershipTest/
// ServiceItemControllerOwnershipTest'te ayrica, daha hizli (Mockito,
// Spring context'siz) test ediliyor -- burada tekrar edilmiyor.
@AutoConfigureMockMvc
class OwnerOnlyEndpointsUnauthenticatedTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void kimliksizIstek_calismaSaatleriUcuReddedilir() throws Exception {
        mockMvc.perform(get("/api/businesses/999999/working-hours"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kimliksizIstek_kapanislarUcuReddedilir() throws Exception {
        mockMvc.perform(get("/api/businesses/999999/closures"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kimliksizIstek_hizmetListesiUcuReddedilir() throws Exception {
        mockMvc.perform(get("/api/service-items/business/999999"))
                .andExpect(status().isUnauthorized());
    }
}
