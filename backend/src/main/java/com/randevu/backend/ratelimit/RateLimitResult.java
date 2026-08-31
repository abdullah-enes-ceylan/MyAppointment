package com.randevu.backend.ratelimit;

// retryAfterSeconds sadece allowed=false iken anlamli -- HTTP'nin standart
// "Retry-After" header'ina dogrudan tasinabilsin diye hem filtre (bkz.
// RateLimitFilter) hem controller/servis (bkz. AuthController) tarafinda
// AYNI hesaptan geliyor, iki farkli yerde iki farkli mantik yazilmiyor.
//
// NOT: static fabrika metotlari YOK -- "allowed()" adinda bir tane
// eklenseydi, record'un kendi "allowed" bilesenine ait otomatik uretilen
// erisimci metoduyla (ayni ad, ayni parametre listesi, farkli donus tipi)
// cakisip derleme hatasi verirdi. Onun yerine cagiran taraf dogrudan
// constructor kullaniyor.
public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
}
