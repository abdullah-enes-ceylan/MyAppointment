// Backend gorece bir yol donuyor (ornegin "/api/business-photos/xxx-card.jpg").
// Faz 3.7 oncesi frontend (Vite dev sunucusu) backend'den farkli bir origin'de
// calisiyordu, bu yuzden mutlak hale getirmek gerekiyordu. Artik hem prod'da
// (Caddy) hem dev'de (Vite proxy, bkz. vite.config.js) ayni origin'deyiz --
// yol oldugu gibi kullanilabilir. Fonksiyon KORUNUYOR (call site'lari
// degistirmemek icin) ama artik sadece null-check yapiyor.
export function resolvePhotoUrl(url: string | null): string | null {
  return url;
}
