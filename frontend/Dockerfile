# syntax=docker/dockerfile:1

# --- Build asamasi ---
FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# VITE_API_URL, npm run build ANINDA bundle'in icine gomuluyor -- backend'in
# aksine, container calisirken degistirilemez (bkz. axios.ts gerekcesi).
# Caddy ayni origin'den hem frontend'i hem backend'i sundugu icin BILEREK
# BOS birakiliyor: "/api/..." goreli yolu tarayicinin zaten bulundugu
# origin'e gider, Caddy onu backend'e yonlendirir.
ARG VITE_API_URL=""
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# --- Calistirma asamasi ---
# Node build'i TAMAMEN disarida kaliyor -- final image'da npm/node_modules/
# kaynak kod yok, sadece derlenmis statik dosyalar + Caddy.
FROM caddy:2-alpine
COPY --from=build /build/dist /srv/frontend
COPY Caddyfile /etc/caddy/Caddyfile
