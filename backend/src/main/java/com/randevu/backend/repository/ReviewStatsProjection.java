package com.randevu.backend.repository;

// Spring Data'nın arayuz tabanli (interface-based) projeksiyonu -- ReviewRepository'deki
// GROUP BY'siz aggregate sorgunun (AVG, COUNT) sonucunu taşır. averageRating
// Double (nullable): hiç yorum yoksa SQL'de AVG() NULL döner, COUNT ise
// hiç yorum yokken bile 0 döner (asla null değil).
public interface ReviewStatsProjection {
    Double getAverageRating();

    Long getReviewCount();
}
