interface RandevumLogoProps {
  className?: string;
}

// Kalp + tokalasan eller motifi -- mevcut logo-icon.png'nin (raster) vektor
// hali, 2. Google AI Studio prototipiyle karsilastirma sonrasi (2026-09-04)
// tasindi. Guven/anlasma/saglik/guzellik/sicak baglanti temasini simgeliyor.
export function RandevumLogo({ className = "w-10 h-10" }: RandevumLogoProps) {
  return (
    <svg viewBox="0 0 240 240" className={className} fill="none" xmlns="http://www.w3.org/2000/svg" aria-label="Randevum">
      <defs>
        <linearGradient id="randevumBlueBadge" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#254aa5" />
          <stop offset="100%" stopColor="#18337a" />
        </linearGradient>
      </defs>
      <circle cx="120" cy="120" r="104" fill="url(#randevumBlueBadge)" />
      <g stroke="#ffffff" strokeWidth="9" strokeLinecap="round" strokeLinejoin="round">
        <path d="M 82 134 C 70 114 62 94 62 76 C 62 52 80 38 104 40 C 116 42 124 50 128 58" />
        <path d="M 96 126 C 88 112 80 96 80 80 C 80 64 92 56 106 58 C 116 60 122 68 126 76" />
        <path d="M 128 58 C 132 50 140 42 152 40 C 176 38 194 52 194 76 C 194 96 184 116 168 136" />
        <path d="M 126 76 C 130 68 136 60 146 58 C 158 56 170 64 170 80 C 170 96 160 112 146 124" />
        <path d="M 152 102 L 120 134 C 116 138 110 136 108 130 C 106 124 110 118 116 114 L 138 92" />
        <rect x="88" y="130" width="22" height="12" rx="6" transform="rotate(40 99 136)" />
        <rect x="98" y="142" width="22" height="12" rx="6" transform="rotate(40 109 148)" />
        <rect x="108" y="154" width="22" height="12" rx="6" transform="rotate(40 119 160)" />
        <rect x="118" y="166" width="22" height="12" rx="6" transform="rotate(40 129 172)" />
        <path d="M 168 136 C 160 152 148 168 138 178" />
        <path d="M 154 142 L 140 158" />
        <path d="M 144 132 L 130 148" />
        <path d="M 134 122 L 120 138" />
      </g>
    </svg>
  );
}
