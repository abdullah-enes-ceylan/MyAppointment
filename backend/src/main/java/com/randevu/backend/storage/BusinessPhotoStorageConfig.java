package com.randevu.backend.storage;

import com.randevu.backend.config.BusinessPhotoProperties;
import com.randevu.backend.config.R2StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Hangi BusinessPhotoStorage implementasyonunun bean olacagini
// app.business-photo.storage-provider'a gore secer (bkz. NOTLAR.md "R2'ye
// tasima" karari). Iki implementasyon da BILEREK @Component DEGIL -- ikisi de
// olsaydi Spring iki bean bulup NoUniqueBeanDefinitionException atardi. Tek
// secim noktasi burasi.
@Configuration
public class BusinessPhotoStorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.business-photo", name = "storage-provider", havingValue = "local",
            matchIfMissing = true)
    public BusinessPhotoStorage localDiskBusinessPhotoStorage(BusinessPhotoProperties properties) {
        return new LocalDiskBusinessPhotoStorage(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.business-photo", name = "storage-provider", havingValue = "r2")
    public BusinessPhotoStorage r2BusinessPhotoStorage(R2StorageProperties properties) {
        return new R2BusinessPhotoStorage(properties);
    }
}
