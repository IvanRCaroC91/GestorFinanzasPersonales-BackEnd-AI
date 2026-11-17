package com.smartkash.identidad.verificacion.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de las reglas del token de verificación del correo: vale 24 horas y una sola vez.
 *
 * <p>La hora se pasa como dato, así que el paso del tiempo se simula sumando duraciones a un
 * instante fijo, sin esperar.</p>
 */
class TokenVerificacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-17T10:00:00Z");
    private static final String HUELLA = "a".repeat(64);
    private static final UUID CUENTA = UUID.randomUUID();

    // ---------- Creación ----------

    @Test
    @DisplayName("un token nuevo caduca a las 24 horas y está sin usar")
    void unTokenNuevoCaducaALas24Horas() {
        TokenVerificacion token = TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA);

        assertThat(token.usuarioId()).isEqualTo(CUENTA);
        assertThat(token.huella()).isEqualTo(HUELLA);
        assertThat(token.creadoEn()).isEqualTo(AHORA);
        assertThat(token.expiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
        assertThat(token.usadoEn()).isNull();
    }

    @Test
    @DisplayName("cada token nuevo tiene su propio identificador")
    void cadaTokenTieneSuIdentificador() {
        assertThat(TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA).id())
                .isNotEqualTo(TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA).id());
    }

    @Test
    @DisplayName("solo acepta una huella, nunca el valor del token")
    void soloAceptaUnaHuella() {
        String valorEnClaro = "valor-del-token-en-claro-que-no-es-una-huella";

        assertThatThrownBy(() -> TokenVerificacion.nuevo(CUENTA, valorEnClaro, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(valorEnClaro);
    }

    @Test
    @DisplayName("debe caducar después de crearse")
    void debeCaducarDespuesDeCrearse() {
        assertThatThrownBy(() -> new TokenVerificacion(UUID.randomUUID(), CUENTA, HUELLA, AHORA, AHORA, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Vigencia ----------

    @Test
    @DisplayName("a las 23 h 59 min todavía se puede usar")
    void unMinutoAntesSePuedeUsar() {
        TokenVerificacion token = TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA);

        assertThat(token.utilizable(AHORA.plus(Duration.ofHours(24)).minusSeconds(60))).isTrue();
    }

    @Test
    @DisplayName("a las 24 h exactas ya no se puede usar")
    void alas24HorasYaNoSePuedeUsar() {
        TokenVerificacion token = TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA);

        assertThat(token.utilizable(AHORA.plus(Duration.ofHours(24)))).isFalse();
    }

    @Test
    @DisplayName("a las 24 h 1 min ya no se puede usar")
    void unMinutoDespuesNoSePuedeUsar() {
        TokenVerificacion token = TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA);

        assertThat(token.utilizable(AHORA.plus(Duration.ofHours(24)).plusSeconds(60))).isFalse();
    }

    // ---------- Un solo uso ----------

    @Test
    @DisplayName("usarlo anota el momento y devuelve un token nuevo; el original no cambia")
    void usarloAnotaElMomento() {
        TokenVerificacion token = TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA);
        Instant momento = AHORA.plus(Duration.ofHours(2));

        TokenVerificacion usado = token.usar(momento);

        assertThat(usado.usadoEn()).isEqualTo(momento);
        assertThat(usado.id()).isEqualTo(token.id());
        assertThat(token.usadoEn()).isNull();
    }

    @Test
    @DisplayName("un token ya usado no se puede volver a usar")
    void unTokenUsadoNoSePuedeReutilizar() {
        TokenVerificacion usado = TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA).usar(AHORA.plusSeconds(60));

        assertThat(usado.utilizable(AHORA.plusSeconds(120))).isFalse();
        assertThatThrownBy(() -> usado.usar(AHORA.plusSeconds(120)))
                .isInstanceOf(EnlaceNoValido.class);
    }

    @Test
    @DisplayName("un token caducado no se puede usar")
    void unTokenCaducadoNoSePuedeUsar() {
        TokenVerificacion token = TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA);

        assertThatThrownBy(() -> token.usar(AHORA.plus(Duration.ofHours(25))))
                .isInstanceOf(EnlaceNoValido.class);
    }

    @Test
    @DisplayName("caducado y ya usado dan exactamente el mismo error")
    void caducadoYUsadoDanElMismoError() {
        TokenVerificacion token = TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA);
        TokenVerificacion usado = token.usar(AHORA.plusSeconds(60));

        EnlaceNoValido porCaducado = org.junit.jupiter.api.Assertions.assertThrows(EnlaceNoValido.class,
                () -> token.usar(AHORA.plus(Duration.ofHours(25))));
        EnlaceNoValido porUsado = org.junit.jupiter.api.Assertions.assertThrows(EnlaceNoValido.class,
                () -> usado.usar(AHORA.plusSeconds(120)));

        assertThat(porUsado.getMessage()).isEqualTo(porCaducado.getMessage()).isEqualTo(EnlaceNoValido.MENSAJE);
    }

    // ---------- Depuración ----------

    @Test
    @DisplayName("el texto de depuración no muestra la huella")
    void elTextoDeDepuracionNoMuestraLaHuella() {
        assertThat(TokenVerificacion.nuevo(CUENTA, HUELLA, AHORA).toString()).doesNotContain(HUELLA);
    }
}
