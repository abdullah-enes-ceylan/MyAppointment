package com.randevu.backend.service;

import com.randevu.backend.config.BusinessPhotoProperties;
import com.randevu.backend.exception.BusinessRuleException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

// Yuklenen bir gorsel dosyasinin dogrulanmasi ve kart/detay boyutlarina
// yeniden kodlanmasi -- BusinessPhotoService'ten (V17 coklu fotograf
// oncesi) BILEREK ayrildi (bkz. NOTLAR.md "SOLID/god-class" notu). Bu
// sinif SADECE "gecerli bir gorsel mi, ise gorsele donustur" sorusuyla
// ilgileniyor -- depolamaya YAZMIYOR, DB'ye DOKUNMUYOR, hangi isletmeye
// ait oldugunu bile bilmiyor. Girdi baytlar, cikti islemis baytlar --
// saf, durumsuz, kendi basina test edilebilir.
@Component
public class BusinessPhotoImageProcessor {

    private final BusinessPhotoProperties properties;

    public BusinessPhotoImageProcessor(BusinessPhotoProperties properties) {
        this.properties = properties;
    }

    public record ProcessedImage(byte[] cardBytes, byte[] detailBytes) {
    }

    public ProcessedImage process(MultipartFile file) {
        byte[] rawBytes = readBytes(file);
        validateSize(rawBytes);
        int originalWidth = validateAndGetWidth(rawBytes);

        byte[] cardBytes = resizeToJpeg(rawBytes, cappedWidth(properties.getCardTargetWidth(), originalWidth));
        byte[] detailBytes = resizeToJpeg(rawBytes, cappedWidth(properties.getDetailTargetWidth(), originalWidth));
        return new ProcessedImage(cardBytes, detailBytes);
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
    // yerde. Istemcinin Content-Type/uzanti iddiasina HIC bakilmiyor --
    // ImageIO.getImageReaders dosyanin kendi baytlarindaki format imzasina
    // (magic bytes) bakiyor. SVG bu mekanizmayla dogal olarak reddediliyor:
    // JDK'nin yerlesik okuyuculari onu hic tanimiyor, okuyucu bulunamayinca
    // asagidaki "readers.hasNext()" kontrolu zaten reddediyor.
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
    // verisini siler: dosya sifirdan yeniden ciziliyor, orijinal baytlar hic
    // diske yazilmiyor. Thumbnailator EXIF orientation'i varsayilan olarak
    // uyguluyor (dondurulmus bir telefon fotografi yamuk cikmiyor).
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
}
