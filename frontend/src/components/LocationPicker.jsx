import { useState } from "react";
import { MapContainer, TileLayer, Marker, useMapEvents } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";

// Vite/webpack ile Leaflet'in varsayilan marker ikonlarinin yolu bozuluyor
// (bundler asset URL'lerini kendi yeniden yaziyor, Leaflet'in beklediği
// göreli yol artık geçerli değil) -- klasik bir Leaflet+bundler sorunu,
// ikonlari elle import edip Leaflet'e açıkça bildiriyoruz.
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
});

const ISTANBUL_CENTER = [41.0082, 28.9784];

// Haritaya tiklamayi yakalayan gorunmez alt bilesen -- react-leaflet'te
// harita olaylari (click, drag, zoom) sadece MapContainer'in ICINDEKI bir
// bilesende useMapEvents ile dinlenebiliyor, MapContainer'in kendisine
// onClick prop'u vermek yeterli degil.
function ClickHandler({ onSelect }) {
  useMapEvents({
    click(e) {
      onSelect(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

// Harita kütüphanesine bağımlı TEK bileşen -- ileride Leaflet yerine
// başka bir servise (ör. Google Maps) geçilmek istenirse SADECE bu
// dosya değişir, onu kullanan LocationTab.jsx hiç değişmeden kalır
// (bkz. CLAUDE.md karar tablosu, 2026-08-23: "Leaflet, beta için;
// gerekirse sonra değiştirilir").
export default function LocationPicker({ latitude, longitude, onChange }) {
  const [position, setPosition] = useState(
    latitude != null && longitude != null ? [latitude, longitude] : null
  );

  function handleSelect(lat, lng) {
    setPosition([lat, lng]);
    onChange(lat, lng);
  }

  return (
    <div className="rounded-xl overflow-hidden border border-white/10">
      <MapContainer
        center={position ?? ISTANBUL_CENTER}
        zoom={position ? 15 : 11}
        style={{ height: "320px", width: "100%" }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> katkıda bulunanlar'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <ClickHandler onSelect={handleSelect} />
        {position && <Marker position={position} />}
      </MapContainer>
    </div>
  );
}
