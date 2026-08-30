import { API_BASE_URL } from "../api/axios";

// Backend gorece bir yol donuyor (ornegin "/api/business-photos/xxx-card.jpg")
// -- frontend (Vite dev sunucusu) farkli bir origin'de calistigi icin bu,
// backend'in taban URL'iyle birlestirilip mutlak hale getirilmeli. url null
// ise (isletme henuz kapak fotografi yuklememisse) oldugu gibi null doner --
// cagiran taraf bu durumda mevcut gradyan kapagi gostermeye devam eder.
export function resolvePhotoUrl(url: string | null): string | null {
  if (!url) return null;
  return `${API_BASE_URL}${url}`;
}
