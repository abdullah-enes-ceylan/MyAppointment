import axios from "axios";

// Eskiden VITE_API_URL ile ayrı bir taban URL taşınıyordu (frontend ve
// backend farklı origin'lerdeyken gerekliydi). Faz 3.7'de hem prod'da
// (Caddy, bkz. Caddyfile) hem dev'de (Vite proxy, bkz. vite.config.js)
// frontend ve backend AYNI origin'den servis ediliyor -- baseURL hiç
// gerekmiyor, "/api/..." göreli yolu zaten doğru yere gidiyor.
const api = axios.create({
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
