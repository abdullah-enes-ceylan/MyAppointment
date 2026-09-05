package com.randevu.backend.dto.response;

// Isletme galerisindeki tek bir fotograf (V17, coklu galeri). displayOrder
// en kucuk olan = kapak -- ama bu DTO'yu tuketen taraf (musteri tarafi
// carousel, PR3) bunu bilmek zorunda degil, liste zaten dogru sirada geliyor
// (bkz. BusinessPhotoRepository.findByBusinessIdOrderByDisplayOrderAscIdAsc).
public record BusinessPhotoResponse(
        Long id,
        String cardUrl,
        String detailUrl,
        short displayOrder) {
}
