package com.randevu.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

// Isletme kapak fotografi yukleme/depolama ayarlari TEK yerde.
//
// AppointmentPolicyProperties ile ayni gerekce: bu degerler birlikte anlam
// tasiyor (boyut siniri, piksel siniri, hedef genislikler) ve saha geri
// bildirimiyle (ornegin "480px bulanik gorunuyor, 640 olsun") kod
// degisikligi/derleme/deploy gerektirmeden ayarlanabilmeli.
@Component
@ConfigurationProperties(prefix = "app.business-photo")
public class BusinessPhotoProperties {

    private static final Logger log = LoggerFactory.getLogger(BusinessPhotoProperties.class);

    private static final String DEFAULT_STORAGE_DIR = "business-photo-storage";
    private static final long DEFAULT_MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int DEFAULT_MAX_IMAGE_WIDTH_PX = 8000;
    private static final int DEFAULT_MAX_IMAGE_HEIGHT_PX = 8000;
    private static final Set<String> DEFAULT_ALLOWED_INPUT_FORMATS = Set.of("JPEG", "PNG");
    private static final int DEFAULT_CARD_TARGET_WIDTH = 480;
    private static final int DEFAULT_DETAIL_TARGET_WIDTH = 1200;
    private static final BusinessPhotoStorageProvider DEFAULT_STORAGE_PROVIDER = BusinessPhotoStorageProvider.LOCAL;
    private static final int DEFAULT_MAX_PHOTOS_PER_BUSINESS = 5;

    // Depolama arka ucu: LOCAL (varsayilan, dev/test) veya R2 (prod). Enum
    // oldugu icin BILEREK asagidaki validate()'e girmiyor -- gecersiz bir
    // deger Spring'in kendi binding'i tarafindan acilista hata verilerek
    // yakalanir, sessiz bir varsayilana dusme riski yok (bkz.
    // BusinessPhotoStorageProvider'daki gerekce).
    private BusinessPhotoStorageProvider storageProvider = DEFAULT_STORAGE_PROVIDER;

    // Fotograflarin diske yazildigi dizin. classpath/static DEGIL -- ayri bir
    // klasor, boylece Spring'in otomatik static resource handler'i uzerinden
    // yanlislikla dogrudan servis edilmez (path traversal/format dogrulamasi
    // atlanmis olur). Uygulamanin calisma dizinine gore cozumlenir.
    private String storageDir = DEFAULT_STORAGE_DIR;

    // Istemciden kabul edilen ham dosyanin ust boyut siniri. Servlet
    // seviyesindeki spring.servlet.multipart.max-file-size bundan BILEREK
    // biraz yuksek tutulur (bkz. application.properties) -- boylece siniri
    // asan normal-boyuttaki bir dosya bizim anlamli hata mesajimiza duser,
    // Spring'in genel teknik hatasina degil.
    private long maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;

    // Decompression bomb korumasi: decode/resize'a hic girmeden, sadece
    // dosya header'indan okunan piksel boyutu bu sinirlari asarsa istek
    // reddedilir. Gercek telefon/DSLR fotograflarinin uzun kenari genelde
    // bu sinirin cok altinda kalir.
    private int maxImageWidthPx = DEFAULT_MAX_IMAGE_WIDTH_PX;
    private int maxImageHeightPx = DEFAULT_MAX_IMAGE_HEIGHT_PX;

    // ImageIO'nun GERCEKTEN tespit ettigi format (istemcinin Content-Type
    // iddiasi degil) bu kumeyle karsilastirilir. SVG bu kumede hic yok --
    // JDK'nin yerlesik ImageIO okuyuculari zaten SVG'yi tanimiyor, ayrica bir
    // "SVG'yi yasakla" kontrolu gerekmiyor.
    private Set<String> allowedInputFormats = new LinkedHashSet<>(DEFAULT_ALLOWED_INPUT_FORMATS);

    // Uretilen turetilmis dosyalarin hedef genisligi (piksel). Kesin degerler
    // tasarim gorseline gore netlesecek (bkz. plan "Acik Kalan Kararlar"
    // madde 4) -- simdilik yer tutucu.
    private int cardTargetWidth = DEFAULT_CARD_TARGET_WIDTH;
    private int detailTargetWidth = DEFAULT_DETAIL_TARGET_WIDTH;

    // Isletme basina en fazla kac fotograf (V17, coklu galeri). Sayi
    // sabit kodlanmiyor -- saha geri bildirimiyle (ornegin "3 az geldi,
    // 5 olsun") kod degisikligi/deploy gerektirmeden ayarlanabilmeli.
    private int maxPhotosPerBusiness = DEFAULT_MAX_PHOTOS_PER_BUSINESS;

    // Gecersiz ayarlar uygulamayi durdurmuyor, guvenli varsayilana dusup
    // uyari logluyor -- AppointmentPolicyProperties.validate ile ayni gerekce.
    @PostConstruct
    public void validate() {
        if (storageDir == null || storageDir.isBlank()) {
            log.warn("app.business-photo.storage-dir geçersiz ({}), varsayılan {} kullanılıyor",
                    storageDir, DEFAULT_STORAGE_DIR);
            storageDir = DEFAULT_STORAGE_DIR;
        }
        if (maxSizeBytes <= 0) {
            log.warn("app.business-photo.max-size-bytes geçersiz ({}), varsayılan {} kullanılıyor",
                    maxSizeBytes, DEFAULT_MAX_SIZE_BYTES);
            maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;
        }
        if (maxImageWidthPx <= 0) {
            log.warn("app.business-photo.max-image-width-px geçersiz ({}), varsayılan {} kullanılıyor",
                    maxImageWidthPx, DEFAULT_MAX_IMAGE_WIDTH_PX);
            maxImageWidthPx = DEFAULT_MAX_IMAGE_WIDTH_PX;
        }
        if (maxImageHeightPx <= 0) {
            log.warn("app.business-photo.max-image-height-px geçersiz ({}), varsayılan {} kullanılıyor",
                    maxImageHeightPx, DEFAULT_MAX_IMAGE_HEIGHT_PX);
            maxImageHeightPx = DEFAULT_MAX_IMAGE_HEIGHT_PX;
        }
        if (allowedInputFormats == null || allowedInputFormats.isEmpty()) {
            log.warn("app.business-photo.allowed-input-formats geçersiz ({}), varsayılan {} kullanılıyor",
                    allowedInputFormats, DEFAULT_ALLOWED_INPUT_FORMATS);
            allowedInputFormats = new LinkedHashSet<>(DEFAULT_ALLOWED_INPUT_FORMATS);
        } else {
            Set<String> normalized = new LinkedHashSet<>();
            for (String format : allowedInputFormats) {
                if (format != null && !format.isBlank()) {
                    normalized.add(format.toUpperCase());
                }
            }
            allowedInputFormats = normalized;
        }
        if (cardTargetWidth <= 0) {
            log.warn("app.business-photo.card-target-width geçersiz ({}), varsayılan {} kullanılıyor",
                    cardTargetWidth, DEFAULT_CARD_TARGET_WIDTH);
            cardTargetWidth = DEFAULT_CARD_TARGET_WIDTH;
        }
        if (detailTargetWidth <= 0) {
            log.warn("app.business-photo.detail-target-width geçersiz ({}), varsayılan {} kullanılıyor",
                    detailTargetWidth, DEFAULT_DETAIL_TARGET_WIDTH);
            detailTargetWidth = DEFAULT_DETAIL_TARGET_WIDTH;
        }
        if (maxPhotosPerBusiness <= 0) {
            log.warn("app.business-photo.max-photos-per-business geçersiz ({}), varsayılan {} kullanılıyor",
                    maxPhotosPerBusiness, DEFAULT_MAX_PHOTOS_PER_BUSINESS);
            maxPhotosPerBusiness = DEFAULT_MAX_PHOTOS_PER_BUSINESS;
        }
    }

    public BusinessPhotoStorageProvider getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(BusinessPhotoStorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public int getMaxImageWidthPx() {
        return maxImageWidthPx;
    }

    public void setMaxImageWidthPx(int maxImageWidthPx) {
        this.maxImageWidthPx = maxImageWidthPx;
    }

    public int getMaxImageHeightPx() {
        return maxImageHeightPx;
    }

    public void setMaxImageHeightPx(int maxImageHeightPx) {
        this.maxImageHeightPx = maxImageHeightPx;
    }

    public Set<String> getAllowedInputFormats() {
        return allowedInputFormats;
    }

    public void setAllowedInputFormats(Set<String> allowedInputFormats) {
        this.allowedInputFormats = allowedInputFormats;
    }

    public int getCardTargetWidth() {
        return cardTargetWidth;
    }

    public void setCardTargetWidth(int cardTargetWidth) {
        this.cardTargetWidth = cardTargetWidth;
    }

    public int getDetailTargetWidth() {
        return detailTargetWidth;
    }

    public void setDetailTargetWidth(int detailTargetWidth) {
        this.detailTargetWidth = detailTargetWidth;
    }

    public int getMaxPhotosPerBusiness() {
        return maxPhotosPerBusiness;
    }

    public void setMaxPhotosPerBusiness(int maxPhotosPerBusiness) {
        this.maxPhotosPerBusiness = maxPhotosPerBusiness;
    }
}
