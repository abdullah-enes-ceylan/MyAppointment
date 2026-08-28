// Hem GÖSTERİM (işletme kartları, detay sayfası, yorum listesi — interactive
// olmadan) hem SEÇİM (yorum formu — interactive ile) için kullanılan tek
// bileşen. value: 0-5 arası (ondalıklı olabilir, ör. 4.3 ortalama puan).
//
// interactive/onChange bilerek ayrı bir union: "interactive true ise onChange
// zorunlu" kuralı ikisini de opsiyonel yapıp çalışma zamanında umut etmek
// yerine tip seviyesinde ifade ediliyor -- interactive=true verilip onChange
// unutulursa derleme hatası alınır.
//
// props destructure EDİLMİYOR: TS'in discriminated union daraltması sadece
// AYNI ifade üzerinde property erişimiyle çalışıyor (test edildi) --
// `const { interactive, onChange } = props` yapılırsa ikisi ayrı birer
// değişkene döner ve aralarındaki bağ kaybolur, `interactive` true olsa
// bile `onChange` yine "possibly undefined" kalır.
type StarRatingProps =
  | { value: number; size?: string; interactive?: false; onChange?: undefined }
  | { value: number; size?: string; interactive: true; onChange: (star: number) => void };

export default function StarRating(props: StarRatingProps) {
  const { value, size = "text-base" } = props;
  const stars = [1, 2, 3, 4, 5];

  return (
    <span className={`inline-flex items-center gap-0.5 ${size}`}>
      {stars.map((star) => {
        const filled = star <= Math.round(value);
        return (
          <span
            key={star}
            onClick={props.interactive ? () => props.onChange(star) : undefined}
            className={`${props.interactive ? "cursor-pointer hover:scale-110 transition-transform" : ""} ${
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
