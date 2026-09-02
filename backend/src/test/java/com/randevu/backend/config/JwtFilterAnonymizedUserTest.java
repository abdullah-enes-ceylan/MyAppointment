package com.randevu.backend.config;

import com.randevu.backend.AbstractIntegrationTest;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.UserRepository;
import com.randevu.backend.service.AccountDeletionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// CustomUserDetailsService'teki enabled = (anonymizedAt == null) yorumu,
// anonimlestirme sonrasi girisin "gercekten kapandigini" iddia ediyor
// (ROADMAP 3.9). Ama JwtFilter, userDetailsService.loadUserByUsername(email)
// cagrisini HICBIR try/catch icine almadan yapiyor -- ve anonymize()
// kullanicinin email'ini DEGISTIRIYOR (deleted-user-{id}@deleted.local).
// Yani anonimlestirmeden ONCE alinmis, suresi henuz DOLMAMIS bir JWT'nin
// subject'i artik VAR OLMAYAN eski email -- loadUserByUsername() enabled=false
// dondurmeden ONCE UsernameNotFoundException firlatiyor (kullanici o email
// ile hic bulunamiyor).
//
// Bu test o senaryoyu CANLI (gercek Postgres + gercek filtre zinciri, mock
// degil) kurup sonucun GERCEKTEN ne oldugunu kanitliyor: RestAuthentication
// EntryPoint'in urettigi duzgun 401 mi, yoksa UsernameNotFoundException'in
// (ExceptionTranslationFilter'dan ONCE calisan JwtFilter'da firlamasi
// yuzunden hic yakalanmayip) Spring Boot'un varsayilan /error'una dusen bir
// 500 mu.
@AutoConfigureMockMvc
class JwtFilterAnonymizedUserTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private AccountDeletionService accountDeletionService;

    private Long createdUserId;

    @AfterEach
    void cleanUp() {
        if (createdUserId != null) {
            userRepository.deleteById(createdUserId);
            createdUserId = null;
        }
    }

    @Test
    void anonimlestirmedenOnceAlinmisTokenIle_anonimlestirmeSonrasiIstekAtilinca() throws Exception {
        String email = "jwt-filter-test-" + UUID.randomUUID() + "@example.com";

        User user = User.builder()
                .name("Test")
                .surName("Kullanici")
                .email(email)
                .password(passwordEncoder.encode("sifre123"))
                .phone("5550000000")
                .role(Role.USER)
                .build();
        user = userRepository.save(user);
        createdUserId = user.getId();

        // Token, anonimlestirmeden ONCE, hala gecerli email'le uretiliyor --
        // gercek dunyada "kullanici anonimlestirmeden hemen once giris yapmis,
        // suresi 24 saat olan JWT'si hala cepte" senaryosu.
        UserDetails userDetailsBeforeAnonymization = userDetailsService.loadUserByUsername(email);
        String tokenIssuedBeforeAnonymization = jwtUtil.generateToken(userDetailsBeforeAnonymization);

        // Ayni akisi (AccountDeletionScheduler'in gracePeriod sonrasi
        // cagirdigi) gercek servisle tetikliyoruz -- user.email burada
        // deleted-user-{id}@deleted.local'a DEGISIYOR.
        accountDeletionService.anonymize(user);

        // Eski (artik var olmayan email'e ait) token ile korumali bir uca istek.
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tokenIssuedBeforeAnonymization))
                .andExpect(status().isUnauthorized());
    }

    // Senaryo 2 (baska bir oturumdan gelen ek soru): requestDeletion() SADECE
    // deletionRequestedAt'i dolduruyor, email/enabled DEGISMIYOR (bkz.
    // CustomUserDetailsService: enabled = anonymizedAt==null, deletionRequestedAt
    // DEGIL). Bu yuzden hesap silme TALEP EDILDIKTEN hemen sonra, anonimlestirme
    // henuz calismamisken, kullanicinin talepten ONCE aldigi eski token'in
    // HALA calismasi bekleniyor -- bilinclilik iddiasini canli kanitliyoruz.
    @Test
    void silmeTalepEdildiAmaHenuzAnonimlestirilmediginde_eskiTokenHalaCalisir() throws Exception {
        String email = "jwt-filter-test-deletion-requested-" + UUID.randomUUID() + "@example.com";

        User user = User.builder()
                .name("Test")
                .surName("Kullanici")
                .email(email)
                .password(passwordEncoder.encode("sifre123"))
                .phone("5550000000")
                .role(Role.USER)
                .build();
        user = userRepository.save(user);
        createdUserId = user.getId();

        UserDetails userDetailsBeforeRequest = userDetailsService.loadUserByUsername(email);
        String tokenIssuedBeforeRequest = jwtUtil.generateToken(userDetailsBeforeRequest);

        // DELETE /api/users/me akisinin cagirdigi ayni servis metodu.
        accountDeletionService.requestDeletion(user, "sifre123");

        User afterRequest = userRepository.findById(createdUserId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(afterRequest.getDeletionRequestedAt()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(afterRequest.getAnonymizedAt()).isNull();

        // Talepten ONCE alinmis token hala AYNI email'e ait -- enabled hala
        // true, bu yuzden 200 bekleniyor, 401 DEGIL.
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tokenIssuedBeforeRequest))
                .andExpect(status().isOk());
    }
}
