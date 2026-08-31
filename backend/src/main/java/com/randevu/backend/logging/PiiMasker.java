package com.randevu.backend.logging;

import java.util.regex.Pattern;

// Loglanacak SERBEST METIN icindeki (DB surucusu/Jackson gibi UCUNCU TARAF
// kutuphanelerin urettigi, bizim kontrol etmedigimiz) e-posta gorunumlu
// alt-dizeleri maskeler. Bilerek genel bir "her logu regex'le tara" filtresi
// DEGIL -- sadece GlobalExceptionHandler'daki iki call site GIBI, ham bir
// exception mesajinin PII tasiyabildigi YERLERE ELLE uygulanir (bkz. o
// siniftaki gerekce). Yeni bir yerde ham exception mesaji loglanacaksa
// CLAUDE.md'deki not hatirlatiyor: bu sinifin oraya da uygulanmasi gerekip
// gerekmedigi degerlendirilmeli.
public final class PiiMasker {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final String MASK = "***@***";

    private PiiMasker() {
    }

    // Mesaj icindeki her e-posta gorunumlu alt-diziyi maskeler; e-posta yoksa
    // veya mesaj null ise oldugu gibi doner.
    public static String maskEmails(String message) {
        if (message == null) {
            return null;
        }
        return EMAIL_PATTERN.matcher(message).replaceAll(MASK);
    }
}
