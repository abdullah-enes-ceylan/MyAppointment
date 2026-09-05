import logoMark from "../assets/randevum_logo_transparent.png";

interface RandevumLogoProps {
  className?: string;
}

// Kalp + tokaLASan eller motifi. Once elle cizilmis bir SVG yaklastirmasiydi
// (2026-09-04) -- artik gercek tasarim varligi kullaniliyor
// (assets/randevum_logo_transparent.png, 2026-09-05). Daire + lacivert
// gradyan arka plan hala CSS ile ciziliyor (SVG'deki ayni <circle> tedavisi)
// -- boylece herhangi bir boyutta netligini korur, sadece ortadaki beyaz
// cizgi sanati raster goruntu.
export function RandevumLogo({ className = "w-10 h-10" }: RandevumLogoProps) {
  return (
    <div
      className={`${className} rounded-full flex items-center justify-center shrink-0 overflow-hidden`}
      style={{ background: "linear-gradient(135deg, #254aa5, #18337a)" }}
    >
      <img src={logoMark} alt="Randevum" className="w-[85%] h-[85%] object-contain" />
    </div>
  );
}
