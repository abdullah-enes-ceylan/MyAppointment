package com.randevu.backend.controller;

import com.randevu.backend.storage.BusinessPhotoStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;

// Isletme kapak fotograflarini servis eden herkese acik uc. Kendi
// controller'imiz uzerinden servis ediyoruz (Spring'in otomatik static
// resource handler'i DEGIL) ki asagidaki path traversal + format
// dogrulamasi HER istekte calissin -- bkz. plan "Isletme Kapak Fotografi"
// madde 1 ve 8.
//
// BILEREK SADECE storage-provider=local iken yayinlaniyor (bkz. NOTLAR.md
// "R2'ye tasima" karari). R2 modunda urlFor() dogrudan CDN URL'i dondugu icin
// tarayici bu uca hic ugramiyor -- eger yine de acik kalsaydi, biri CDN
// yerine bu ucu (VPS uzerinden) kullanip edge cache'i atlayarak hem
// bandwidth'imizi hem R2'nin class B operasyon kotasini tuketebilirdi.
@RestController
@RequestMapping("/api/business-photos")
@ConditionalOnProperty(prefix = "app.business-photo", name = "storage-provider", havingValue = "local",
        matchIfMissing = true)
public class BusinessPhotoController {

    // Sadece BusinessPhotoService'in urettigi kalibi kabul eder:
    // {uuid}-card.jpg / {uuid}-detail.jpg. Bunun DISINDA hicbir sey --
    // ozellikle "/", ".." gibi dizin degistirici karakterler bu regex'i
    // gecemez.
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-(card|detail)\\.jpg$");

    private final BusinessPhotoStorage photoStorage;

    public BusinessPhotoController(BusinessPhotoStorage photoStorage) {
        this.photoStorage = photoStorage;
    }

    // Uymayan istekler 404 doner (400 DEGIL) -- "yanlis bicim" ile "boyle bir
    // dosya yok" arasinda istemciye fark belli edilmez, path traversal
    // denemesi icin keskif bilgisi sizdirilmaz. Ikinci savunma katmani
    // LocalDiskBusinessPhotoStorage.resolve'da: regex bir sekilde atlatilsa
    // bile depolama dizini disina cikis orada da engellenir.
    //
    // photo_key her yeni yuklemede yeni bir UUID oldugu icin URL icerik-
    // adresli -- ayni URL asla farkli bir gorsele donusmez, bu yuzden
    // immutable cache guvenle verilebilir (bkz. plan madde 6).
    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> getPhoto(@PathVariable String filename) {
        if (!FILENAME_PATTERN.matcher(filename).matches()) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] content = photoStorage.read(filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
