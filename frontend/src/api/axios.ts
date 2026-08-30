import axios from "axios";

// Eskiden http://localhost:8080 sabit kodluydu — deploy'da (frontend ve
// backend farklı adreslerde çalıştığında) kırılırdı. .env'den okunuyor,
// VITE_API_URL tanımlı değilse yerel geliştirme varsayılanına düşer.
//
// Ayrıca export ediliyor: backend'in döndürdüğü göreceli görsel yolları
// (ör. "/api/business-photos/xxx-card.jpg") <img src> için mutlak hale
// getirilirken kullanılıyor -- bkz. utils/photo.ts.
export const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

// Request Interceptor — Her isteğe otomatik JWT token ekler
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor — 401 ve 403'ü ayrı ele alır.
// 401 (token yok/geçersiz/süresi dolmuş): oturum artık anlamsız,
// zorla çıkış yapılır — RestAuthenticationEntryPoint (backend, Faz 0.4)
// bu durumda gerçekten 401 döndüğü için bu artık güvenilir bir sinyal.
// 403 (token geçerli ama BU işlem için yetki yok — örn. başka bir
// işletmenin verisine erişmeye çalışmak) BİLEREK burada ele alınmıyor:
// kullanıcının oturumu geçerli, onu zorla çıkışa atmak yanlış olur.
// Sayfa bileşenleri kendi catch bloklarında err.response?.data?.message
// ile bu hatayı zaten gösteriyor (bkz. BusinessDetailPage, PendingAppointments).
//
// error parametresinin tipini axios'un kendi tanımı (AxiosInterceptorRejected)
// belirliyor, burada elle bir tip bildirimi eklenmiyor -- axios bu callback'i
// serbest tipli kabul ediyor, kütüphane sınırında olan ve bizim eklemediğimiz
// bir gevşeklik.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem("token");
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

export default api;
