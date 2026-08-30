package com.randevu.backend.service;

import com.randevu.backend.config.BusinessPhotoProperties;
import com.randevu.backend.entity.Business;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.storage.BusinessPhotoStorage;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.UUID;

// Isletme kapak fotografi yukleme akisinin tamami: dogrulama, yeniden kodlama,
// depolama, eskiyi temizleme. Detayli mimari gerekce plan dosyasinda
// ("Isletme Kapak Fotografi" PR3) -- burada sadece NEDEN bu sirada oldugu
// ozetleniyor.
//
// Isletme satirinin kilitlenmesi (eszamanli yukleme korumasi) BU sinifta
// DEGIL, BusinessService.swapPhotoKey'de -- bkz. o metottaki self-invocation
// gerekcesi.
@Service
public class BusinessPhotoService {

    private static final Logger log = LoggerFactory.getLogger(BusinessPhotoService.class);

    private static final String CARD_SUFFIX = "-card.jpg";
    private static final String DETAIL_SUFFIX = "-detail.jpg";

    private final BusinessService businessService;
    private final BusinessPhotoStorage photoStorage;
    private final BusinessPhotoProperties properties;

    public BusinessPhotoService(BusinessService businessService, BusinessPhotoStorage photoStorage,
            BusinessPhotoProperties properties) {
        this.businessService = businessService;
        this.photoStorage = photoStorage;
        this.properties = properties;
    }

    public Business uploadPhoto(Long businessId, MultipartFile file) {
        byte[] rawBytes = readBytes(file);
        validateSize(rawBytes);
        int originalWidth = validateAndGetWidth(rawBytes);

        byte[] cardBytes = resizeToJpeg(rawBytes, cappedWidth(properties.getCardTargetWidth(), originalWidth));
        byte[] detailBytes = resizeToJpeg(rawBytes, cappedWidth(properties.getDetailTargetWidth(), originalWidth));

        // Sira BILEREK bu: once yeni dosyalar diske yazilir, SONRA DB
        // guncellenir, EN SON eski dosyalar silinir. DB guncellemesi
        // basarisiz olsa bile isletme eski (hala calisan) fotografini
        // gostermeye devam eder -- kullanici hicbir sey kaybetmez. Sira
        // tersine cevrilseydi bir hatada isletme fotografsiz kalabilirdi.
        String newKey = UUID.randomUUID().toString();
        writeNewFiles(newKey, cardBytes, detailBytes);

        BusinessService.PhotoKeySwapResult swap = businessService.swapPhotoKey(businessId, newKey);

        if (swap.previousPhotoKey() != null) {
            deleteOldFilesQuietly(swap.previousPhotoKey());
        }

        return swap.business();
    }

    // Multipart govdesinin okunmasi basarisiz olursa (ornegin istemci
    // yukleme sirasinda baglantiyi kesti) bu istemci tarafinda sonlanan bir
    // durum -- 500 degil, anlamli bir BusinessRuleException.
    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("Yüklenen dosya okunamadı.");
        }
    }

    private void validateSize(byte[] rawBytes) {
        if (rawBytes.length == 0) {
            throw new BusinessRuleException("Boş dosya yüklenemez.");
        }
        if (rawBytes.length > properties.getMaxSizeBytes()) {
            long maxMb = properties.getMaxSizeBytes() / (1024 * 1024);
            throw new BusinessRuleException("Dosya boyutu çok büyük (en fazla " + maxMb + " MB olabilir).");
        }
    }

    // Icerik-seviyesinde format dogrulama + decompression bomb korumasi TEK
    // yerde (bkz. plan madde 3 ve 4). Istemcinin Content-Type/uzanti iddiasina
    // HIC bakilmiyor -- ImageIO.getImageReaders dosyanin kendi baytlarindaki
    // format imzasina (magic bytes) bakiyor. SVG bu mekanizmayla dogal olarak
    // reddediliyor: JDK'nin yerlesik okuyuculari onu hic tanimiyor, okuyucu
    // bulunamayinca asagidaki "readers.hasNext()" kontrolu zaten reddediyor.
    //
    // getWidth(0)/getHeight(0) sadece dosyanin HEADER'ini okur, goruntuyu tam
    // decode ETMEDEN piksel boyutunu verir -- bu yuzden asil (bellek acisindan
    // pahali) decode/resize adimindan ONCE, guvenle cagrilabiliyor.
    private int validateAndGetWidth(byte[] rawBytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            if (iis == null) {
                throw new BusinessRuleException("Desteklenmeyen veya bozuk görsel dosyası.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new BusinessRuleException("Desteklenmeyen veya bozuk görsel dosyası.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                String format = reader.getFormatName().toUpperCase();
                if (!properties.getAllowedInputFormats().contains(format)) {
                    throw new BusinessRuleException("Desteklenmeyen görsel formatı: " + format);
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > properties.getMaxImageWidthPx() || height > properties.getMaxImageHeightPx()) {
                    throw new BusinessRuleException("Görsel çözünürlüğü çok yüksek (en fazla "
                            + properties.getMaxImageWidthPx() + "x" + properties.getMaxImageHeightPx()
                            + " piksel olabilir).");
                }
                return width;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BusinessRuleException("Görsel okunamadı.");
        }
    }

    // Kucuk bir gorseli hedef genislige buyutmuyoruz -- orijinalden buyuk bir
    // hedef istenirse (ornegin kucuk bir profil fotosu detay boyutu icin
    // yetersizse) orijinal genislik kullanilir, aksi halde cikti bulanik olur.
    private int cappedWidth(int targetWidth, int originalWidth) {
        return Math.min(targetWidth, originalWidth);
    }

    // Her iki turetilmis dosya da JPEG'e yeniden kodlanir -- PNG girisse bile.
    // Bu hem tutarli bir cikti saglar hem format-tabanli metadata/polyglot
    // risklerini SIFIRLAR hem de EXIF bloguyla birlikte olasi GPS konum
    // verisini siler (bkz. plan madde 5): dosya sifirdan yeniden ciziliyor,
    // orijinal baytlar hic diske yazilmiyor. Thumbnailator EXIF orientation'i
    // varsayilan olarak uyguluyor (dondurulmus bir telefon fotografi yamuk
    // cikmiyor).
    //
    // Buraya kadar validateAndGetWidth basariyla gectiyse dosya zaten bizim
    // izin verdigimiz formatta ve piksel siniri icinde -- bu yuzden burada
    // olusacak bir IOException gercek bir bozukluktan cok, beklenmeyen bir
    // decode hatasidir; yine de istemcinin gonderdigi dosyayla ilgili
    // oldugu icin BusinessRuleException.
    private byte[] resizeToJpeg(byte[] source, int targetWidth) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(source))
                    .width(targetWidth)
                    .outputFormat("jpg")
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessRuleException("Görsel işlenemedi.");
        }
    }

    // Disk yazma hatasi istemcinin sucu degil, bizim altyapimizin sorunu --
    // BusinessRuleException(409) yerine UncheckedIOException firlatiliyor ki
    // GlobalExceptionHandler'in genel Exception yakalayicisi bunu 500 olarak
    // ele alsin ve ERROR seviyesinde loglasin.
    private void writeNewFiles(String key, byte[] cardBytes, byte[] detailBytes) {
        try {
            photoStorage.store(key + CARD_SUFFIX, cardBytes);
            photoStorage.store(key + DETAIL_SUFFIX, detailBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("İşletme fotoğrafı diske yazılamadı.", e);
        }
    }

    // Silme basarisiz olursa istek yine de BASARILI sayilir -- yeni fotograf
    // zaten kaydedildi ve calisiyor. Hata sadece WARN olarak loglanir; bunu
    // "yukleme basarisiz" sebebi yapmak, calisan bir ozelligi disk temizligi
    // sorunu yuzunden kullaniciya hata gibi gostermek olurdu (bkz. plan
    // madde 7 "Silme basarisiz olursa").
    private void deleteOldFilesQuietly(String oldKey) {
        try {
            photoStorage.delete(oldKey + CARD_SUFFIX);
            photoStorage.delete(oldKey + DETAIL_SUFFIX);
        } catch (IOException e) {
            log.warn("Eski işletme fotoğrafı silinemedi (key={}): {}", oldKey, e.getMessage());
        }
    }
}
