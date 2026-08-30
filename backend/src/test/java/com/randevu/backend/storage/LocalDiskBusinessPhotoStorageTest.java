package com.randevu.backend.storage;

import com.randevu.backend.config.BusinessPhotoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// LocalDiskBusinessPhotoStorage'in round-trip ve path traversal davranisi.
//
// Spring baglami gerektirmiyor (TimeConfigTest ile ayni gerekce): arayuzun
// tek implementasyonunu dogrudan kuruyoruz, @PostConstruct'i elle cagiriyoruz.
class LocalDiskBusinessPhotoStorageTest {

    @TempDir
    Path tempDir;

    private LocalDiskBusinessPhotoStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        BusinessPhotoProperties properties = new BusinessPhotoProperties();
        properties.setStorageDir(tempDir.resolve("photos").toString());
        storage = new LocalDiskBusinessPhotoStorage(properties);
        storage.ensureStorageDirExists();
    }

    @Test
    @DisplayName("store edilen dosya read ile aynen geri gelir")
    void storeAndReadRoundTrip() throws IOException {
        byte[] content = "sahte-jpeg-baytlari".getBytes(StandardCharsets.UTF_8);

        storage.store("abc-card.jpg", content);

        assertArrayEquals(content, storage.read("abc-card.jpg"));
    }

    @Test
    @DisplayName("urlFor kendi servis ucumuza isaret eder")
    void urlForPointsToOwnServingEndpoint() {
        assertEquals("/api/business-photos/abc-card.jpg", storage.urlFor("abc-card.jpg"));
    }

    @Test
    @DisplayName("delete sonrasi dosya gercekten diskten kayboluyor")
    void deleteRemovesFileFromDisk() throws IOException {
        storage.store("abc-detail.jpg", "veri".getBytes(StandardCharsets.UTF_8));

        storage.delete("abc-detail.jpg");

        assertThrows(IOException.class, () -> storage.read("abc-detail.jpg"));
    }

    @Test
    @DisplayName("var olmayan dosyayi silmek sessizce noop -- hata firlatmaz")
    void deleteNonExistentFileIsNoop() {
        assertDoesNotThrow(() -> storage.delete("yok-card.jpg"));
    }

    @Test
    @DisplayName("path traversal denemesi storageDir disina cikamaz")
    void pathTraversalAttemptIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.store("../../evil.jpg", "zararli".getBytes(StandardCharsets.UTF_8)));
    }
}
