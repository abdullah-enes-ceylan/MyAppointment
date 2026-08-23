// Hem GÖSTERİM (işletme kartları, detay sayfası, yorum listesi — interactive
// olmadan) hem SEÇİM (yorum formu — interactive ile) için kullanılan tek
// bileşen. value: 0-5 arası (ondalıklı olabilir, ör. 4.3 ortalama puan).
export default function StarRating({ value, onChange, size = "text-base", interactive = false }) {
  const stars = [1, 2, 3, 4, 5];

  return (
    <span className={`inline-flex items-center gap-0.5 ${size}`}>
      {stars.map((star) => {
        const filled = star <= Math.round(value);
        return (
          <span
            key={star}
            onClick={interactive ? () => onChange(star) : undefined}
            className={`${interactive ? "cursor-pointer hover:scale-110 transition-transform" : ""} ${
              filled ? "text-amber-400" : "text-slate-600"
            }`}
          >
            ★
          </span>
        );
      })}
    </span>
  );
}
