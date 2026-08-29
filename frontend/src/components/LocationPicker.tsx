import { useState } from "react";
import { MapContainer, TileLayer, Marker, useMapEvents } from "react-leaflet";
import L, { type LatLngTuple } from "leaflet";
import "leaflet/dist/leaflet.css";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";

// Vite/webpack ile Leaflet'in varsayilan marker ikonlarinin yolu bozuluyor
// (bundler asset URL'lerini kendi yeniden yaziyor, Leaflet'in beklediği
// göreli yol artık geçerli değil) -- klasik bir Leaflet+bundler sorunu,
// ikonlari elle import edip Leaflet'e açıkça bildiriyoruz.
//
// _getIconUrl, Leaflet'in kendi calisma zamaninda var olan ama
// @types/leaflet'in bilerek disa acmadigi (private/internal) bir alan --
// bu yuzden L.Icon.Default.prototype uzerinde dogrudan erisim tip hatasi
// verir. Asagidaki tek satirlik daraltma, resmi leaflet+bundler
// workaround'unun TS karsiligi (turu genel bir belirsiz tipe acmadan
// sadece bu tek alanin varligini bildiriyor).
delete (L.Icon.Default.prototype as { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
});

const ISTANBUL_CENTER: LatLngTuple = [41.0082, 28.9784];

// Haritaya tiklamayi yakalayan gorunmez alt bilesen -- react-leaflet'te
// harita olaylari (click, drag, zoom) sadece MapContainer'in ICINDEKI bir
// bilesende useMapEvents ile dinlenebiliyor, MapContainer'in kendisine
// onClick prop'u vermek yeterli degil.
function ClickHandler({ onSelect }: { onSelect: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onSelect(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

// readOnly/onChange bilerek discriminated union: BusinessDetailPage'deki
// (musterinin GORDUGU, tiklanamayan) kullanim onChange vermiyor, LocationTab'daki
// (isletme sahibinin konum SECTIGI) kullanim veriyor -- "readOnly=false iken
// onChange zorunlu" kurali boylece derleme zamaninda kontrol ediliyor
// (bkz. StarRating'deki ayni desen, PR1).
type LocationPickerProps = {
  latitude?: number | null;
  longitude?: number | null;
  height?: number;
} & (
  | { readOnly: true; onChange?: undefined }
  | { readOnly?: false; onChange: (lat: number, lng: number) => void }
);

// Harita kütüphanesine bağımlı TEK bileşen -- ileride Leaflet yerine
// başka bir servise (ör. Google Maps) geçilmek istenirse SADECE bu
// dosya değişir, onu kullanan yerler (LocationTab, BusinessDetailPage)
// hiç değişmeden kalır (bkz. CLAUDE.md karar tablosu, 2026-08-23:
// "Leaflet, beta için; gerekirse sonra değiştirilir").
export default function LocationPicker(props: LocationPickerProps) {
  const { latitude, longitude, height = 320 } = props;
  const [position, setPosition] = useState<LatLngTuple | null>(
    latitude != null && longitude != null ? [latitude, longitude] : null
  );

  function handleSelect(lat: number, lng: number) {
    setPosition([lat, lng]);
    if (!props.readOnly) {
      props.onChange(lat, lng);
    }
  }

  return (
    <div className="rounded-xl overflow-hidden border border-white/10">
      <MapContainer
        center={position ?? ISTANBUL_CENTER}
        zoom={position ? 15 : 11}
        style={{ height: `${height}px`, width: "100%" }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> katkıda bulunanlar'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {!props.readOnly && <ClickHandler onSelect={handleSelect} />}
        {position && <Marker position={position} />}
      </MapContainer>
    </div>
  );
}
