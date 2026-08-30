package com.randevu.backend.storage;

import com.randevu.backend.config.BusinessPhotoProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// BusinessPhotoStorage'in yerel dosya sistemi implementasyonu.
//
// Dizin classpath/static DEGIL -- app.business-photo.storage-dir ile
// yapilandirilan, uygulamanin calisma dizinine gore cozumlenen ayri bir
// klasor. Boylece Spring'in otomatik static resource handler'i uzerinden
// yanlislikla servis edilmez; tek erisim yolu kendi controller'imizdaki path
// traversal + format dogrulamali GET ucu (bkz. plan PR3).
//
// Kalici disk riski BILEREK kabul edildi (bkz. CLAUDE.md karar tablosu):
// deploy ortaminin dosya sistemi kalici olmayan bir platform olursa tum
// fotograflar sessizce kaybolur. BusinessPhotoStorage arayuzu sayesinde bu,
// ileride tek bir implementasyon degisikligiyle (S3/R2) cozulebilir.
@Component
public class LocalDiskBusinessPhotoStorage implements BusinessPhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskBusinessPhotoStorage.class);

    private final Path storageDir;

    public LocalDiskBusinessPhotoStorage(BusinessPhotoProperties properties) {
        this.storageDir = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
    }

    // Dizin ilk yukleme isteginde degil, uygulama acilirken bir kez
    // olusturulur -- ilk istek "dizin yok" hatasiyla ugrasmasin.
    @PostConstruct
    void ensureStorageDirExists() throws IOException {
        Files.createDirectories(storageDir);
    }

    @Override
    public void store(String filename, byte[] content) throws IOException {
        Files.write(resolve(filename), content);
    }

    @Override
    public void delete(String filename) throws IOException {
        boolean deleted = Files.deleteIfExists(resolve(filename));
        if (!deleted) {
            log.warn("Silinecek isletme fotografi zaten yok: {}", filename);
        }
    }

    @Override
    public String urlFor(String filename) {
        return "/api/business-photos/" + filename;
    }

    @Override
    public byte[] read(String filename) throws IOException {
        return Files.readAllBytes(resolve(filename));
    }

    // Path traversal'a karsi ikinci savunma katmani (birincisi servis ucundaki
    // regex, bkz. plan madde 8): resolve edilen mutlak yolun hala storageDir
    // ICINDE oldugu ayrica dogrulanir. Regex ileride gevsetilse bile dizin
    // disina cikisi engeller.
    private Path resolve(String filename) {
        Path resolved = storageDir.resolve(filename).normalize();
        if (!resolved.startsWith(storageDir)) {
            throw new IllegalArgumentException("Geçersiz dosya adı: " + filename);
        }
        return resolved;
    }
}
