package com.randevu.backend.controller;

import com.randevu.backend.AbstractIntegrationTest;
import com.randevu.backend.config.AccountDeletionProperties;
import com.randevu.backend.config.JwtUtil;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessCategory;
import com.randevu.backend.entity.Favorite;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.AppointmentRepository;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.FavoriteRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.UserRepository;
import com.randevu.backend.service.AccountDeletionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Faz 3.9 -- frontend'in "hesabınız silinecek" banner'ını ve işletme
// sahibi için "N randevunuz iptal edilecek" onay uyarısını
// gösterebilmesi için GEREKEN alanların GERÇEKTEN API yanıtında geldiğini
// kanıtlıyor. Kod okuyup "dönüyor gibi görünüyor" demek yerine gerçek
// HTTP isteği + gerçek JWT + gerçek Postgres (AbstractIntegrationTest) ile
// JSON gövdesi doğrudan okunuyor -- JwtFilterAnonymizedUserTest'teki aynı
// desen (token'ı login akışından değil doğrudan JwtUtil'den üretiyoruz,
// tek amacımız bu alanların yanıtta olup olmadığı, login akışı ayrı test
// edilmiş zaten).
@AutoConfigureMockMvc
class AccountDeletionResponseFieldsTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServiceItemRepository serviceItemRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private FavoriteRepository favoriteRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private AccountDeletionService accountDeletionService;
    @Autowired
    private AccountDeletionProperties accountDeletionProperties;

    private static final String RAW_PASSWORD = "sifre123";

    private User createUser(Role role) {
        String unique = String.valueOf(System.nanoTime());
        User user = User.builder()
                .name("Test").surName("Kullanici").email("adr-" + unique + "@example.com")
                .password(passwordEncoder.encode(RAW_PASSWORD)).phone("5550000000").role(role).build();
        return userRepository.save(user);
    }

    private String tokenFor(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        return jwtUtil.generateToken(userDetails);
    }

    @Test
    @DisplayName("GET /me: talep yokken silme alanlarının hepsi null")
    void meCevabinda_talepYoksa_silmeAlanlariNull() throws Exception {
        User user = createUser(Role.USER);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionRequestedAt").value(nullValue()))
                .andExpect(jsonPath("$.identityAnonymizationDeadlineAt").value(nullValue()))
                .andExpect(jsonPath("$.businessReversalDeadlineAt").value(nullValue()));
    }

    @Test
    @DisplayName("GET /me: USER talep verince identityAnonymizationDeadlineAt dolu, businessReversalDeadlineAt null")
    void meCevabinda_userTalepVerince_sadeceIdentityDeadlineDolu() throws Exception {
        User user = createUser(Role.USER);
        accountDeletionService.requestDeletion(user, RAW_PASSWORD);
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        LocalDateTime expectedIdentityDeadline = reloaded.getDeletionRequestedAt()
                .plus(accountDeletionProperties.getGracePeriod());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionRequestedAt").value(org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.identityAnonymizationDeadlineAt").value(expectedIdentityDeadline.toString()))
                .andExpect(jsonPath("$.businessReversalDeadlineAt").value(nullValue()));
    }

    @Test
    @DisplayName("GET /me: BUSINESS_OWNER talep verince İKİ deadline de dolu")
    void meCevabinda_businessOwnerTalepVerince_ikiDeadlineDeDolu() throws Exception {
        User owner = createUser(Role.BUSINESS_OWNER);
        accountDeletionService.requestDeletion(owner, RAW_PASSWORD);
        User reloaded = userRepository.findById(owner.getId()).orElseThrow();
        LocalDateTime expectedIdentityDeadline = reloaded.getDeletionRequestedAt()
                .plus(accountDeletionProperties.getGracePeriod());
        LocalDateTime expectedReversalDeadline = reloaded.getDeletionRequestedAt()
                .plus(accountDeletionProperties.getBusinessReversalWindow());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityAnonymizationDeadlineAt").value(expectedIdentityDeadline.toString()))
                .andExpect(jsonPath("$.businessReversalDeadlineAt").value(expectedReversalDeadline.toString()));
    }

    @Test
    @DisplayName("GET /me/deletion-impact: randevusu olmayan BUSINESS_OWNER için 0")
    void deletionImpact_randevusuOlmayanIsletmeSahibiIcinSifir() throws Exception {
        User owner = createUser(Role.BUSINESS_OWNER);

        mockMvc.perform(get("/api/users/me/deletion-impact").header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedAppointmentCount").value(0));
    }

    @Test
    @DisplayName("GET /me/deletion-impact: PENDING+APPROVED sayılır, CANCELLED sayılmaz")
    void deletionImpact_pendingVeApprovedSayarCancelledSaymaz() throws Exception {
        User owner = createUser(Role.BUSINESS_OWNER);
        User customer = createUser(Role.USER);
        Business business = businessRepository.save(Business.builder()
                .name("Test İşletme").address("Adres").owner(owner).category(BusinessCategory.HAIRDRESSER).build());
        ServiceItem serviceItem = serviceItemRepository.save(ServiceItem.builder()
                .name("Hizmet").description("d").price(BigDecimal.TEN).durationInMinutes(30).business(business)
                .build());

        for (AppointmentStatus status : new AppointmentStatus[] {
                AppointmentStatus.PENDING, AppointmentStatus.APPROVED, AppointmentStatus.CANCELLED }) {
            appointmentRepository.save(Appointment.builder()
                    .status(status).business(business).customer(customer).serviceItem(serviceItem)
                    .appointmentDate(LocalDateTime.now().plusDays(5))
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        mockMvc.perform(get("/api/users/me/deletion-impact").header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedAppointmentCount").value(2));
    }

    @Test
    @DisplayName("GET /me/deletion-impact: USER rolünde her zaman 0, kendi randevuları olsa bile")
    void deletionImpact_userRolundeHerZamanSifir() throws Exception {
        User owner = createUser(Role.BUSINESS_OWNER);
        User customer = createUser(Role.USER);
        Business business = businessRepository.save(Business.builder()
                .name("Test İşletme 2").address("Adres").owner(owner).category(BusinessCategory.HAIRDRESSER).build());
        ServiceItem serviceItem = serviceItemRepository.save(ServiceItem.builder()
                .name("Hizmet").description("d").price(BigDecimal.TEN).durationInMinutes(30).business(business)
                .build());
        appointmentRepository.save(Appointment.builder()
                .status(AppointmentStatus.PENDING).business(business).customer(customer).serviceItem(serviceItem)
                .appointmentDate(LocalDateTime.now().plusDays(5))
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/users/me/deletion-impact").header("Authorization", "Bearer " + tokenFor(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedAppointmentCount").value(0));
    }

    @Test
    @DisplayName("GET /api/businesses/my: talep sonrası suspended=true dönüyor")
    void myBusinesses_talepSonrasi_suspendedTrueDoner() throws Exception {
        User owner = createUser(Role.BUSINESS_OWNER);
        businessRepository.save(Business.builder()
                .name("Askıya Alınacak İşletme").address("Adres").owner(owner)
                .category(BusinessCategory.HAIRDRESSER).build());

        mockMvc.perform(get("/api/businesses/my").header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].suspended").value(false));

        accountDeletionService.requestDeletion(owner, RAW_PASSWORD);

        mockMvc.perform(get("/api/businesses/my").header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].suspended").value(true));
    }

    // Faz 3.9 -- GET /api/businesses/{id}'ye sahip için eklenen istisnanın
    // GERÇEKTEN sahiplik kontrolü olduğunu (sadece "kimliği doğrulanmış
    // herhangi bir kullanıcı" değil) kanıtlıyor. Bu ayrım kritik: ikincisi
    // olsaydı askıdaki işletme HER giriş yapmış kullanıcıya görünür olurdu,
    // "hiçbir yoldan görüntülenemesin" garantisi tekrar delinirdi.
    @Test
    @DisplayName("GET /businesses/{id}: askıdaki işletmede sahip 200 alır, BAŞKA giriş yapmış kullanıcı YİNE 404 alır")
    void getBusinessById_askidaySahipGorurBaskaKullaniciYineDortYuzDortAlir() throws Exception {
        User owner = createUser(Role.BUSINESS_OWNER);
        User stranger = createUser(Role.USER);
        Business business = businessRepository.save(Business.builder()
                .name("Sahiplik Testi İşletmesi").address("Adres").owner(owner)
                .category(BusinessCategory.HAIRDRESSER).build());

        accountDeletionService.requestDeletion(owner, RAW_PASSWORD);

        mockMvc.perform(get("/api/businesses/" + business.getId()).header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true));

        mockMvc.perform(get("/api/businesses/" + business.getId()).header("Authorization", "Bearer " + tokenFor(stranger)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/businesses/" + business.getId()))
                .andExpect(status().isNotFound());
    }

    // Faz 3.9 -- FavoriteService.getFavoriteBusinesses'te bulunan gerçek
    // sızıntı: businessRepository ÜZERİNDEN değil Favorite.getBusiness()
    // ilişkisi üzerinden erişildiği için önceki "businessRepository çağrı
    // noktaları" taramasında hiç görünmüyordu. Bu test, favorilenen bir
    // işletme askıya alınınca GET /api/favorites/me'den GERÇEKTEN düştüğünü
    // kanıtlıyor.
    @Test
    @DisplayName("GET /favorites/me: askıya alınan işletme favori listesinden düşer")
    void getMyFavorites_askidakiIsletmeFavorilerdenDuser() throws Exception {
        User owner = createUser(Role.BUSINESS_OWNER);
        User customer = createUser(Role.USER);
        Business business = businessRepository.save(Business.builder()
                .name("Favorilenen İşletme").address("Adres").owner(owner)
                .category(BusinessCategory.HAIRDRESSER).build());
        favoriteRepository.save(Favorite.builder()
                .user(customer).business(business).createdAt(LocalDateTime.now()).build());

        mockMvc.perform(get("/api/favorites/me").header("Authorization", "Bearer " + tokenFor(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(business.getId()));

        accountDeletionService.requestDeletion(owner, RAW_PASSWORD);

        mockMvc.perform(get("/api/favorites/me").header("Authorization", "Bearer " + tokenFor(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
