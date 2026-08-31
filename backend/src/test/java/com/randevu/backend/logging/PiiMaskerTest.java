package com.randevu.backend.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// GlobalExceptionHandler'daki iki gercek call site'in loglayabildigi ham
// mesaj BICIMLERINI birebir kullanir -- regex ileride bozulursa (ornegin
// biri "sadelestirmek" isterse) bu test kirilir.
class PiiMaskerTest {

    @Test
    void postgresBenzersizlikIhlaliMesajindakiEpostayiMaskeler() {
        // DataIntegrityViolationException.getMostSpecificCause().getMessage()'in
        // gercek bicimi: users.email UNIQUE kisitini ihlal eden bir yaris
        // durumunda Postgres suru bunu uretiyor.
        String raw = "ERROR: duplicate key value violates unique constraint \"users_email_key\"\n"
                + "  Detail: Key (email)=(victim@example.com) already exists.";

        String masked = PiiMasker.maskEmails(raw);

        assertThat(masked).doesNotContain("victim@example.com");
        assertThat(masked).contains("***@***");
        assertThat(masked).contains("users_email_key");
    }

    @Test
    void jacksonParseHatasiMesajindakiEpostayiMaskeler() {
        // HttpMessageNotReadableException.getMostSpecificCause().getMessage()'in
        // tipik bicimi: Jackson, ayristiramadigi ham degeri mesaja gomuyor.
        String raw = "Cannot deserialize value of type `java.lang.String` from String "
                + "\"attacker@example.com\": not a valid enum value";

        String masked = PiiMasker.maskEmails(raw);

        assertThat(masked).doesNotContain("attacker@example.com");
        assertThat(masked).contains("***@***");
    }

    @Test
    void birdenFazlaEpostayiAyniMesajdaMaskeler() {
        String raw = "a@example.com ve b@example.com cakisiyor";

        String masked = PiiMasker.maskEmails(raw);

        assertThat(masked).isEqualTo("***@*** ve ***@*** cakisiyor");
    }

    @Test
    void epostaIcermeyenMesajiDegistirmedenBirakir() {
        String raw = "Key (appointment_id, notification_type)=(1, PENDING_EXPIRY_WARNING) already exists.";

        assertThat(PiiMasker.maskEmails(raw)).isEqualTo(raw);
    }

    @Test
    void nullMesajiOldugu_gibi_birakir() {
        assertThat(PiiMasker.maskEmails(null)).isNull();
    }
}
