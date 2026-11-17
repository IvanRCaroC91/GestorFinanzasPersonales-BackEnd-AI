package com.smartkash.identidad.verificacion.aplicacion;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.verificacion.dominio.CorreoDeVerificacion;
import com.smartkash.identidad.verificacion.dominio.TokenVerificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del caso de uso {@link EmitirEnlaceDeVerificacion}: generar un enlace nuevo, guardar
 * solo su huella, invalidar los anteriores y enviar el correo.
 */
class EmitirEnlaceDeVerificacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-17T10:00:00Z");
    private static final String BASE = "https://app.ejemplo";

    private final PuertosFalsos puertos = new PuertosFalsos();
    private final EmitirEnlaceDeVerificacion emitir = new EmitirEnlaceDeVerificacion(
            puertos.tokens, puertos.generador, puertos.huellas, puertos.enviador, puertos.unidad,
            Clock.fixed(AHORA, ZoneOffset.UTC), BASE);

    private final Usuario ana = Usuario.nuevo("Ana", "Ana@Correo.com", new CorreoNormalizado("Ana@Correo.com"), "hash");

    @Test
    @DisplayName("guarda un token de esa cuenta, con la huella del valor generado y 24 horas de vigencia")
    void guardaElToken() {
        emitir.emitir(ana);

        assertThat(puertos.tokens.guardados).hasSize(1);
        TokenVerificacion token = puertos.tokens.guardados.getFirst();
        assertThat(token.usuarioId()).isEqualTo(ana.id());
        assertThat(token.huella()).isEqualTo(PuertosFalsos.sha256(puertos.generador.ultimo));
        assertThat(token.huella()).isNotEqualTo(puertos.generador.ultimo);
        assertThat(token.creadoEn()).isEqualTo(AHORA);
        assertThat(token.expiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("envía el correo a la cuenta con el enlace que lleva el valor en claro")
    void enviaElCorreo() {
        emitir.emitir(ana);

        assertThat(puertos.enviador.enviados).containsExactly(
                new CorreoDeVerificacion("Ana@Correo.com", "Ana",
                        BASE + "/verificar-correo#token=" + puertos.generador.ultimo));
    }

    @Test
    @DisplayName("invalida los anteriores y guarda el nuevo dentro de la transacción; envía después, fuera")
    void ordenDeLosPasos() {
        emitir.emitir(ana);

        assertThat(puertos.llamadas).containsExactly(
                "invalidarPendientes (dentro)", "guardarToken (dentro)", "enviarCorreo (fuera)");
    }

    @Test
    @DisplayName("si el correo no sale, el error llega a quien llama; el enlace ya quedó guardado")
    void siElCorreoFallaElErrorSale() {
        puertos.enviador.falla = true;

        assertThatThrownBy(() -> emitir.emitir(ana)).isInstanceOf(IllegalStateException.class);
        assertThat(puertos.tokens.guardados).hasSize(1);
    }
}
