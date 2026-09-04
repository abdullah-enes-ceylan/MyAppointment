package com.randevu.backend.storage;

import com.randevu.backend.config.R2StorageProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.StringJoiner;

// BusinessPhotoStorage'in Cloudflare R2 implementasyonu (bkz. NOTLAR.md "R2'ye
// tasima" karari). R2, S3 API'siyle uyumlu oldugu icin AWS'in kendi SDK'sini
// farkli bir endpoint'e isaret ederek kullaniyoruz -- ayri bir R2-ozel istemci
// yok.
//
// urlFor() kendi servis ucumuza DEGIL, dogrudan public CDN'e (custom domain)
// isaret eder -- bu yuzden okuma trafigi hic bu sinifa/backend'e ugramaz,
// SADECE store/delete/read (yukleme, silme, olasi bir admin/debug ihtiyaci)
// gercekten cagrilir. read()'in yine de tam calisir olmasi BILINCLI: arayuzde
// tanimli bir metodun bir implementasyonda calismamasi Liskov ihlali olurdu.
public class R2BusinessPhotoStorage implements BusinessPhotoStorage {

    private final String bucketName;
    private final String publicBaseUrl;
    private final S3Client client;

    public R2BusinessPhotoStorage(R2StorageProperties properties) {
        requireConfigured(properties);
        this.bucketName = properties.getBucketName();
        this.publicBaseUrl = stripTrailingSlash(properties.getPublicBaseUrl());
        this.client = S3Client.builder()
                .endpointOverride(URI.create("https://" + properties.getAccountId() + ".r2.cloudflarestorage.com"))
                // R2'de "region" kavrami yok, S3 SDK'si yine de bir deger
                // istiyor -- "auto" bunun icin ayrilmis sabit deger.
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey())))
                // Path-style adresleme (https://endpoint/bucket/key) -- R2'nin
                // kendi S3 uyumluluk dokumantasyonunun onerdigi, virtual-hosted
                // stile (https://bucket.endpoint/key) gore daha az yuzeyli secim.
                .forcePathStyle(true)
                // Varsayilan (Apache/Netty) yerine hafif, senkron istemci --
                // bkz. pom.xml'deki url-connection-client gerekcesi.
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .overrideConfiguration(b -> b.apiCallTimeout(Duration.ofSeconds(15)))
                .build();
    }

    // R2StorageProperties BILEREK kendi validasyonunu yapmiyor (bkz. o
    // sinifin gerekcesi) -- storage-provider=local iken bu alanlar hic
    // doldurulmamis olabilir ve bu SORUN DEGIL. Zorunluluk kontrolu SADECE
    // bu bean gercekten olusturulmaya calisildiginda (yani storage-provider=r2
    // secildiginde) burada calisir.
    private void requireConfigured(R2StorageProperties properties) {
        StringJoiner missing = new StringJoiner(", ");
        if (isBlank(properties.getAccountId())) {
            missing.add("app.business-photo.r2.account-id");
        }
        if (isBlank(properties.getBucketName())) {
            missing.add("app.business-photo.r2.bucket-name");
        }
        if (isBlank(properties.getAccessKeyId())) {
            missing.add("app.business-photo.r2.access-key-id");
        }
        if (isBlank(properties.getSecretAccessKey())) {
            missing.add("app.business-photo.r2.secret-access-key");
        }
        if (isBlank(properties.getPublicBaseUrl())) {
            missing.add("app.business-photo.r2.public-base-url");
        }
        if (missing.length() > 0) {
            throw new IllegalStateException(
                    "storage-provider=r2 secildi ama zorunlu ayarlar eksik: " + missing);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public void store(String filename, byte[] content) throws IOException {
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(filename)
                    .contentType("image/jpeg")
                    // Object key'in kendisi her yuklemede yeni bir UUID
                    // tasidigi icin ayni URL asla farkli bir gorsele
                    // donusmuyor -- immutable cache guvenle, upload aninda
                    // (serve-time yerine) metadata olarak verilebiliyor.
                    .cacheControl("public, max-age=31536000, immutable")
                    .build(), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new IOException("İşletme fotoğrafı R2'ye yazılamadı: " + filename, e);
        }
    }

    @Override
    public void delete(String filename) throws IOException {
        try {
            // S3/R2'nin DeleteObject'i dogasi geregi idempotent -- nesne zaten
            // yoksa hata vermez, LocalDiskBusinessPhotoStorage.delete'teki
            // "yok say" davranisiyla ayni sonuc, ekstra kontrole gerek yok.
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(filename)
                    .build());
        } catch (SdkException e) {
            throw new IOException("İşletme fotoğrafı R2'den silinemedi: " + filename, e);
        }
    }

    @Override
    public String urlFor(String filename) {
        return publicBaseUrl + "/" + filename;
    }

    @Override
    public byte[] read(String filename) throws IOException {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(filename)
                    .build()).asByteArray();
        } catch (SdkException e) {
            throw new IOException("İşletme fotoğrafı R2'den okunamadı: " + filename, e);
        }
    }
}
