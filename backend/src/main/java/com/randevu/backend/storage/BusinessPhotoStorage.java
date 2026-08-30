package com.randevu.backend.storage;

import java.io.IOException;

// Isletme kapak fotografi icin depolama soyutlamasi.
//
// Bugun tek implementasyon yerel disk (LocalDiskBusinessPhotoStorage).
// Ileride S3/R2'ye gecis istenirse SADECE yeni bir implementasyon + Spring
// bean degisimi gerekir -- bu arayuze bagli kalan servis/controller kodu hic
// degismez. Projenin NotificationPort icin benimsedigi soyutlama deseniyle
// ayni felsefe (bkz. CLAUDE.md karar tablosu).
public interface BusinessPhotoStorage {

    // Verilen dosya adiyla baytlari kalici depoya yazar (varsa uzerine yazar).
    void store(String filename, byte[] content) throws IOException;

    // Verilen dosya adini depodan siler. Dosya zaten yoksa sessizce noop --
    // cagiran taraf (BusinessPhotoService) "eski fotograf var miydi" sorusunu
    // zaten kendisi cevaplayip sadece varsa cagiriyor.
    void delete(String filename) throws IOException;

    // Bu dosya adina karsilik gelen, istemcinin cekebilecegi URL. Yerel
    // diskte kendi servis ucumuza (/api/business-photos/{filename}) isaret
    // eder; S3/R2'ye gecilirse dogrudan bucket/CDN URL'i donebilir.
    String urlFor(String filename);

    // Dosyanin ham baytlarini okur -- servis ucu (GET /api/business-photos/..)
    // tarafindan kullanilir. urlFor kendi controller'imiza isaret ettigi
    // surece bu metot gerekli; dogrudan bucket/CDN'e yonlendiren bir
    // implementasyonda hic cagrilmayabilir.
    byte[] read(String filename) throws IOException;
}
