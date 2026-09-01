import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Faz 3.7: prod'da Caddy frontend+backend'i AYNI origin'den sunuyor
    // (bkz. Caddyfile). Dev'de bu proxy AYNI modeli taklit ediyor -- Vite
    // dev sunucusu (5173) "/api" ve "/actuator" isteklerini backend'e
    // (varsayilan dev portu, bkz. CLAUDE.md komutlari: ./mvnw spring-boot:run)
    // yonlendiriyor. Boylece dev ve prod AYNI origin modelinde calisiyor --
    // "prod'a ozgu, farkli origin'den kaynaklanan bir hata sadece prod'da
    // ortaya cikar" riski ortadan kalkiyor, VITE_API_URL gibi ayri bir
    // taban-URL degiskenine de artik hic gerek yok (bkz. axios.ts).
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: true },
      "/actuator": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
})
