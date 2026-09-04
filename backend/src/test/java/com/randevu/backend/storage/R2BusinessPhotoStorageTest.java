package com.randevu.backend.storage;

import com.randevu.backend.config.R2StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// R2BusinessPhotoStorage'in AGA (gercek R2'ye) hic baglanmadan test edilebilen
// kismi: constructor'daki zorunluluk dogrulamasi ve urlFor'un saf string
// birlestirme mantigi. store/delete/read'in R2'ye karsi GERCEKTEN dogru
// calistigi bu testle KANITLANMIYOR -- o kanit R5'te gercek bir staging
// bucket'a karsi yapilacak (bkz. NOTLAR.md "R2'ye tasima" karari, "mock testi
// yeterli kanit degil" notu).
class R2BusinessPhotoStorageTest {

    @Test
    @DisplayName("Eksik ayarlarla olusturulmaya calisilinca acikca hangi anahtarlarin eksik oldugunu soyleyen IllegalStateException firlar")
    void eksikAyarlarlaOlusturulamaz() {
        R2StorageProperties properties = new R2StorageProperties();

        assertThatThrownBy(() -> new R2BusinessPhotoStorage(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.business-photo.r2.account-id")
                .hasMessageContaining("app.business-photo.r2.bucket-name")
                .hasMessageContaining("app.business-photo.r2.access-key-id")
                .hasMessageContaining("app.business-photo.r2.secret-access-key")
                .hasMessageContaining("app.business-photo.r2.public-base-url");
    }

    @Test
    @DisplayName("urlFor, publicBaseUrl sonundaki slash'i normalize eder")
    void urlForTrailingSlashNormalizasyonu() {
        R2StorageProperties withSlash = validProperties();
        withSlash.setPublicBaseUrl("https://cdn.randevumweb.com/");
        R2StorageProperties withoutSlash = validProperties();
        withoutSlash.setPublicBaseUrl("https://cdn.randevumweb.com");

        String expected = "https://cdn.randevumweb.com/abc-card.jpg";

        assertThat(new R2BusinessPhotoStorage(withSlash).urlFor("abc-card.jpg")).isEqualTo(expected);
        assertThat(new R2BusinessPhotoStorage(withoutSlash).urlFor("abc-card.jpg")).isEqualTo(expected);
    }

    // S3Client.builder().build() gercek bir aga baglanti KURMUYOR (tembel) --
    // bu yuzden gercek olmayan (sahte) kimlik bilgileriyle bile constructor
    // guvenle cagrilabiliyor, testler R2'ye hic istek atmadan calisiyor.
    private R2StorageProperties validProperties() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setAccountId("test-account");
        properties.setBucketName("test-bucket");
        properties.setAccessKeyId("test-access-key");
        properties.setSecretAccessKey("test-secret-key");
        properties.setPublicBaseUrl("https://cdn.randevumweb.com");
        return properties;
    }
}
