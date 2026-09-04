package com.randevu.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Cloudflare R2 baglanti bilgileri -- SADECE app.business-photo.storage-provider=r2
// iken anlamli/zorunlu (bkz. BusinessPhotoStorageProvider). Bu sinif BILEREK
// kendi @PostConstruct dogrulamasini YAPMIYOR: eger burada "accessKeyId bos
// olamaz" gibi bir kontrol koysaydik, bu deger sadece r2 modunda gerekliyken,
// storageProvider=local olan (yani R2 kimlik bilgisi hic girilmemis) her dev/test
// acilisinda da patlardi. Zorunluluk kontrolu R2BusinessPhotoStorage'in kendi
// constructor'inda yapilir -- yani SADECE r2 bean'i gercekten olusturulmaya
// calisildiginda calisir, tam da AppointmentPolicyProperties'teki "gecerli
// olmayan/eksik deger SESSIZCE gecistirilmez" felsefesiyle tutarli ama gereksiz
// yere dev ortamini kilitlemeden.
//
// KRITIK: Bu sinifa asla @ToString veya @Data eklenmesin. secretAccessKey alani
// herhangi bir logda/exception mesajinda duz metin gorunmemeli -- projenin
// mevcut DTO deseni (bkz. LoginRequest) zaten @Getter/@Setter disinda hicbir sey
// eklemiyor, buradaki gerekce ayni deseni GUVENLIK acisindan da zorunlu kiliyor.
@Component
@ConfigurationProperties(prefix = "app.business-photo.r2")
@Getter
@Setter
public class R2StorageProperties {

    // Cloudflare hesap kimligi -- sir DEGIL, S3 API endpoint'ini kurmak icin
    // kullanilir (https://{accountId}.r2.cloudflarestorage.com).
    private String accountId;

    // Tek bucket, sadece bu depolamaya ayrilmis (bkz. NOTLAR.md).
    private String bucketName;

    // R2 API token'inin S3-uyumlu kimlik bilgileri -- SIR. Sadece ortam
    // degiskeninden gelir, application-dev.properties disinda hicbir dosyaya
    // duz metin yazilmaz (o dosya da gitignore'da).
    private String accessKeyId;
    private String secretAccessKey;

    // Herkese acik okuma icin custom domain, sonunda "/" OLMADAN
    // (ornek: https://cdn.randevumweb.com). urlFor() birlestirirken kendi "/"
    // ekler -- burada cift slash'a karsi normalize edilir.
    private String publicBaseUrl;
}
