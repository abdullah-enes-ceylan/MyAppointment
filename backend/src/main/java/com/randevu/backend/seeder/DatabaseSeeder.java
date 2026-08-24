package com.randevu.backend.seeder;

import com.randevu.backend.entity.*;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.UserRepository;
import com.randevu.backend.repository.WorkingHourRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

// @Profile("dev") KRITIK: bu sinif olmadan, prod'da veritabani ilk acildiginda
// bos oldugu icin (userRepository.count() > 0 kontrolu gecer) sahte Istanbul
// isletmeleri GERCEK production veritabanina yazilirdi. Eskiden bu sinif
// hicbir profil kisitlamasi olmadan HER ortamda calisiyordu.
@Profile("dev")
@Component
public class DatabaseSeeder implements CommandLineRunner {

        private final UserRepository userRepository;
        private final BusinessRepository businessRepository;
        private final ServiceItemRepository serviceItemRepository;
        private final WorkingHourRepository workingHourRepository;
        private final PasswordEncoder passwordEncoder;

        public DatabaseSeeder(UserRepository userRepository,
                        BusinessRepository businessRepository,
                        ServiceItemRepository serviceItemRepository,
                        WorkingHourRepository workingHourRepository,
                        PasswordEncoder passwordEncoder) {
                this.userRepository = userRepository;
                this.businessRepository = businessRepository;
                this.serviceItemRepository = serviceItemRepository;
                this.workingHourRepository = workingHourRepository;
                this.passwordEncoder = passwordEncoder;
        }

        @Override
        public void run(String... args) {
                if (userRepository.count() > 0) {
                        System.out.println("⏭️  Veritabanında veri mevcut, seeder atlaniyor.");
                        return;
                }

                System.out.println("🌱 Veritabanı boş — test verileri oluşturuluyor...");

                // ═══════════════════════════════════════════
                // 1. KULLANICILAR
                // ═══════════════════════════════════════════
                User normalUser = userRepository.save(User.builder()
                                .name("Ahmet")
                                .surName("Yılmaz")
                                .email("ahmet@test.com")
                                .password(passwordEncoder.encode("123456"))
                                .phone("05301234567")
                                .role(Role.USER)
                                .build());

                User adminUser = userRepository.save(User.builder()
                                .name("Zeynep")
                                .surName("Kaya")
                                .email("zeynep@test.com")
                                .password(passwordEncoder.encode("123456"))
                                .phone("05559876543")
                                .role(Role.ADMIN)
                                .build());

                System.out.println("   ✅ 2 kullanıcı oluşturuldu.");

                // ═══════════════════════════════════════════
                // 2. İŞLETMELER (Her kategori için 2 adet)
                // ═══════════════════════════════════════════

                // --- HAIRDRESSER (berber/erkek kuaförü/kadın kuaförü hepsi burada,
                //     ayrım ServedGender'da -- bkz. V10 migration) ---
                Business b1 = saveBusiness("Stil Kuaför", "Bağdat Caddesi No:45, Kadıköy", "02161234567",
                                "Modern saç tasarım stüdyosu", adminUser, "09:00", "20:00",
                                BusinessCategory.HAIRDRESSER, ServedGender.UNISEX, 40.9770, 29.0570);
                Business b2 = saveBusiness("Makas Atölyesi", "İstiklal Caddesi No:120, Beyoğlu", "02129876543",
                                "Profesyonel saç bakım merkezi", adminUser, "08:30", "19:30",
                                BusinessCategory.HAIRDRESSER, ServedGender.FEMALE, 41.0335, 28.9770);
                Business b3 = saveBusiness("Gentleman Barber", "Nişantaşı Abdi İpekçi Cad. No:12", "02122345678",
                                "Klasik erkek kuaförü deneyimi", adminUser, "09:00", "21:00",
                                BusinessCategory.HAIRDRESSER, ServedGender.MALE, 41.0480, 28.9940);
                Business b4 = saveBusiness("Usta Berber", "Tunalı Hilmi Cad. No:88, Ankara", "03124567890",
                                "Geleneksel berber sanatı", adminUser, "08:00", "20:00",
                                BusinessCategory.HAIRDRESSER, ServedGender.MALE, 39.9010, 32.8590);

                // --- BEAUTY_SALON ---
                Business b5 = saveBusiness("Glow Beauty Studio", "Bağdat Cad. No:200, Kadıköy", "02163456789",
                                "Cilt bakımı ve güzellik uzmanı", adminUser, "10:00", "19:00",
                                BusinessCategory.BEAUTY_SALON, ServedGender.FEMALE, 40.9690, 29.0720);
                Business b6 = saveBusiness("Elmas Güzellik Salonu", "Kızılay Meydanı No:5, Ankara", "03125678901",
                                "Premium güzellik hizmetleri", adminUser, "09:30", "20:30",
                                BusinessCategory.BEAUTY_SALON, ServedGender.FEMALE, 39.9200, 32.8540);

                // --- SPA_WELLNESS ---
                // Kadınlara özel spa/hamam Türkiye'de yaygın bir işletme türü;
                // ServedGender filtresinin gerçekçi test edilebilmesi için
                // spa kategorisinde de kadın-özel örnekler var.
                Business b7 = saveBusiness("Zen Spa & Masaj", "Çeşme Marina, İzmir", "02326789012",
                                "Huzur ve rahatlama merkezi", adminUser, "10:00", "22:00",
                                BusinessCategory.SPA_WELLNESS, ServedGender.UNISEX, 38.3230, 26.3060);
                Business b8 = saveBusiness("Aqua Wellness Center", "Ataşehir Bulvarı No:33, İstanbul", "02167890123",
                                "Termal su terapileri", adminUser, "09:00", "21:00",
                                BusinessCategory.SPA_WELLNESS, ServedGender.UNISEX, 40.9920, 29.1270);
                Business b15 = saveBusiness("Lotus Kadınlar Spa", "Bağdat Cad. No:310, Kadıköy", "02163334455",
                                "Kadınlara özel masaj ve bakım merkezi", adminUser, "10:00", "20:00",
                                BusinessCategory.SPA_WELLNESS, ServedGender.FEMALE, 40.9880, 29.0300);
                Business b16 = saveBusiness("Hera Hamam & Spa", "Çankaya Cinnah Cad. No:41, Ankara", "03124445566",
                                "Geleneksel hamam ve kese, sadece kadınlara", adminUser, "09:00", "21:00",
                                BusinessCategory.SPA_WELLNESS, ServedGender.FEMALE, 39.9080, 32.8620);
                Business b17 = saveBusiness("Serenity Kadın Wellness", "Alsancak Kıbrıs Şehitleri Cad. No:78, İzmir",
                                "02325556677", "Kadınlara özel yoga, masaj ve cilt bakımı", adminUser,
                                "09:30", "20:30", BusinessCategory.SPA_WELLNESS, ServedGender.FEMALE,
                                38.4320, 27.1440);

                // --- NAIL_STUDIO ---
                Business b9 = saveBusiness("Tırnak Sanatı Stüdyo", "Alsancak Kordon No:15, İzmir", "02328901234",
                                "Yaratıcı tırnak tasarımları", adminUser, "10:00", "19:00",
                                BusinessCategory.NAIL_STUDIO, ServedGender.FEMALE, 38.4370, 27.1420);
                Business b10 = saveBusiness("Parlak Tırnaklar", "Cevahir AVM Kat:3, İstanbul", "02129012345",
                                "Profesyonel nail art merkezi", adminUser, "10:30", "21:30",
                                BusinessCategory.NAIL_STUDIO, ServedGender.FEMALE, 41.0650, 28.9930);

                // --- MAKEUP_STUDIO ---
                Business b11 = saveBusiness("Glamour Makyaj Stüdyo", "Nişantaşı Valikonağı Cad. No:7", "02120123456",
                                "Düğün ve özel gün makyajı", adminUser, "09:00", "18:00",
                                BusinessCategory.MAKEUP_STUDIO, ServedGender.FEMALE, 41.0490, 28.9950);
                Business b12 = saveBusiness("Rouge Beauty Lab", "Bostancı Köprüsü Yanı No:22", "02161234568",
                                "Profesyonel makyaj uygulamaları", adminUser, "10:00", "19:00",
                                BusinessCategory.MAKEUP_STUDIO, ServedGender.FEMALE, 40.9560, 29.0930);

                // --- TATTOO_STUDIO ---
                Business b13 = saveBusiness("İnk Master Tattoo", "Kadıköy Moda Cad. No:55", "02162345679",
                                "Özel tasarım dövme stüdyosu", adminUser, "12:00", "22:00",
                                BusinessCategory.TATTOO_STUDIO, ServedGender.UNISEX, 40.9820, 29.0260);
                Business b14 = saveBusiness("Black Needle Studio", "Karaköy Mumhane Cad. No:18", "02123456780",
                                "Minimal ve fine-line dövme uzmanı", adminUser, "11:00", "21:00",
                                BusinessCategory.TATTOO_STUDIO, ServedGender.UNISEX, 41.0250, 28.9770);

                // Onaylı rozetinin görünür olması için birkaç demo işletme
                // işaretlendi -- gerçek admin onay akışı henüz yok (bkz. V8 migration).
                markVerified(b1, b3, b5, b7);

                System.out.println("   ✅ 17 işletme oluşturuldu.");

                // ═══════════════════════════════════════════
                // 3. HİZMETLER (Her işletmeye en az 2 adet)
                // ═══════════════════════════════════════════

                // HAIRDRESSER
                saveService("Saç Kesimi", "Profesyonel saç kesimi ve şekillendirme", 250, 45, b1);
                saveService("Saç Boyama", "Tam saç boyama işlemi", 500, 90, b1);
                saveService("Fön & Şekillendirme", "Fön çekimi ve şekillendirme", 150, 30, b1);

                saveService("Saç Kesimi", "Klasik saç kesimi", 200, 40, b2);
                saveService("Ombre / Balyaj", "Modern renk geçişi tekniği", 800, 120, b2);

                // HAIRDRESSER — erkeğe hizmet verenler
                saveService("Saç Kesimi", "Erkek saç kesimi", 200, 30, b3);
                saveService("Sakal Tıraşı", "Klasik ustura ile sakal tıraşı", 150, 20, b3);
                saveService("Saç + Sakal Kombo", "Saç kesimi ve sakal düzenleme paketi", 300, 45, b3);

                saveService("Saç Kesimi", "Geleneksel erkek tıraşı", 180, 30, b4);
                saveService("Sakal Şekillendirme", "Profesyonel sakal bakımı", 120, 20, b4);

                // BEAUTY_SALON
                saveService("Cilt Bakımı", "Derin cilt temizliği ve nemlendirme", 400, 60, b5);
                saveService("Kaş Tasarımı", "İpek kirpik ve kaş şekillendirme", 200, 30, b5);

                saveService("Hydrafacial", "Cilt yenileme ve parlatma", 600, 60, b6);
                saveService("Bölgesel Ağda", "Yüz ve vücut ağda uygulaması", 250, 30, b6);

                // SPA_WELLNESS
                saveService("İsveç Masajı", "Klasik rahatlama masajı", 500, 60, b7);
                saveService("Aroma Terapi", "Esansiyel yağlarla terapi masajı", 600, 75, b7);
                saveService("Hamam Paketi", "Geleneksel Türk hamamı deneyimi", 450, 90, b7);

                saveService("Sırt Masajı", "Yoğun sırt ve omuz masajı", 350, 45, b8);
                saveService("Hot Stone Masaj", "Sıcak taş terapisi", 700, 90, b8);

                saveService("Kadın Masaj Seansı", "Kadın terapist ile rahatlama masajı", 550, 60, b15);
                saveService("Cilt Bakımı & Maske", "Yüz bakımı ve nemlendirme", 400, 45, b15);

                saveService("Kese & Köpük", "Geleneksel hamam ritüeli", 350, 60, b16);
                saveService("Hamam & Masaj Paketi", "Kese, köpük ve masaj", 750, 120, b16);

                saveService("Yoga Seansı", "Grup yoga dersi", 250, 60, b17);
                saveService("Aroma Terapi Masajı", "Esansiyel yağlarla masaj", 600, 75, b17);

                // NAIL_STUDIO
                saveService("Manikür", "Klasik manikür uygulaması", 150, 30, b9);
                saveService("Protez Tırnak", "Jel protez tırnak yapımı", 400, 60, b9);

                saveService("Pedikür", "Ayak bakımı ve pedikür", 200, 40, b10);
                saveService("Nail Art", "Özel tırnak tasarımı ve süsleme", 350, 50, b10);

                // MAKEUP_STUDIO
                saveService("Gelin Makyajı", "Düğün günü özel gelin makyajı", 1500, 90, b11);
                saveService("Günlük Makyaj", "Doğal ve şık günlük makyaj", 400, 45, b11);

                saveService("Özel Gün Makyajı", "Davet ve organizasyon makyajı", 600, 60, b12);
                saveService("Makyaj Dersi", "Birebir makyaj eğitimi", 800, 90, b12);

                // TATTOO_STUDIO
                saveService("Küçük Dövme", "5cm'e kadar minimal dövme", 500, 60, b13);
                saveService("Orta Boy Dövme", "10-20cm arası özel tasarım", 1500, 120, b13);

                saveService("Fine-Line Dövme", "İnce çizgi minimal dövme", 600, 60, b14);
                saveService("Dövme Kapama", "Eski dövme üzerine kapama çalışması", 2000, 180, b14);

                System.out.println("   ✅ 36 hizmet oluşturuldu.");
                System.out.println("🎉 Seeder tamamlandı! Test verileri hazır.");
        }

        // ─── Yardımcı Metotlar ────────────────────────

        // lat/lng YENİ eklendi: eskiden seeder hiç koordinat yazmıyordu, bu
        // yüzden "yakınımdakiler" (Faz 2.8) demo veride pratikte hiçbir sonuç
        // döndürmüyordu -- LocationService'in bounding-box sorgusu NULL
        // koordinatlı satırlarla hiç eşleşmiyor. Aşağıdaki koordinatlar
        // adreslere yaklaşık olarak denk gelen DEMO değerleri, gerçek ölçüm
        // değil; amaç özelliğin çalıştığının görülebilmesi.
        private Business saveBusiness(String name, String address, String phone,
                        String description, User owner,
                        String open, String close, BusinessCategory category,
                        ServedGender servedGender, double latitude, double longitude) {
                LocalTime openTime = LocalTime.parse(open);
                LocalTime closeTime = LocalTime.parse(close);

                Business business = businessRepository.save(Business.builder()
                                .name(name)
                                .address(address)
                                .phone(phone)
                                .description(description)
                                .owner(owner)
                                .openTime(openTime)
                                .closeTime(closeTime)
                                .category(category)
                                .servedGender(servedGender)
                                .latitude(latitude)
                                .longitude(longitude)
                                .build());

                saveDefaultWorkingHours(business, openTime, closeTime);
                return business;
        }

        // Faz 1.6'nin WorkingHour tablosunun GERCEKTEN kullanildigini gostermek
        // icin her isletmeye haftanin 7 gunu ayni saatlerle olusturuluyor —
        // kapali gun/ogle molasi gibi ozel durumlar seed verisine bilerek
        // eklenmedi (demo veriyi gereksiz karmasiklastirirdi); Faz 1.8'in
        // paneli acildiginda isletme sahibi bunlari kendisi ozellestirecek.
        private void saveDefaultWorkingHours(Business business, LocalTime openTime, LocalTime closeTime) {
                for (DayOfWeek day : DayOfWeek.values()) {
                        workingHourRepository.save(WorkingHour.builder()
                                        .business(business)
                                        .dayOfWeek(day)
                                        .openTime(openTime)
                                        .closeTime(closeTime)
                                        .isClosed(false)
                                        .build());
                }
        }

        private void markVerified(Business... businesses) {
                for (Business business : businesses) {
                        business.setVerified(true);
                        businessRepository.save(business);
                }
        }

        // Parametre bilerek double: 30 cagri noktasinda "saveService(..., 250, ...)"
        // gibi duz sayisal literaller kullaniliyor, hepsini BigDecimal.valueOf(250)
        // yazmaya zorlamak yerine donusum burada, tek yerde yapiliyor.
        private void saveService(String name, String description, double price,
                        int duration, Business business) {
                serviceItemRepository.save(ServiceItem.builder()
                                .name(name)
                                .description(description)
                                .price(BigDecimal.valueOf(price))
                                .durationInMinutes(duration)
                                .business(business)
                                .build());
        }
}
