package com.smartkash.identidad.verificacion.aplicacion;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.EstadoCuenta;
import com.smartkash.identidad.cuenta.dominio.Rol;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.verificacion.dominio.DemasiadosEnvios;
import com.smartkash.identidad.verificacion.dominio.TokenVerificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del caso de uso {@link ReenviarVerificacion}: a quién se reenvía, cuándo no se envía
 * nada y el tope de 3 enlaces por hora.
 *
 * <p>Usa el caso de uso real de emisión de enlaces, con los mismos puertos falsos.</p>
 */
class ReenviarVerificacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-17T10:00:00Z");

    private final PuertosFalsos puertos = new PuertosFalsos();
    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final ReenviarVerificacion reenviar = new ReenviarVerificacion(
            puertos.usuarios, puertos.tokens,
            new EmitirEnlaceDeVerificacion(puertos.tokens, puertos.generador, puertos.huellas,
                    puertos.enviador, puertos.unidad, reloj, "https://app.ejemplo"),
            reloj);

    @Test
    @DisplayName("a una cuenta pendiente de verificar se le envía un enlace nuevo")
    void aUnaCuentaPendienteSeLeEnvia() {
        Usuario cuenta = cuentaEn(EstadoCuenta.PENDIENTE_VERIFICACION);

        reenviar.ejecutar(cuenta.id());

        assertThat(puertos.enviador.enviados).hasSize(1);
        assertThat(puertos.enviador.enviados.getFirst().destinatario()).isEqualTo(cuenta.correoOriginal());
        assertThat(puertos.tokens.guardados).extracting(TokenVerificacion::usuarioId).containsExactly(cuenta.id());
    }

    @ParameterizedTest(name = "a una cuenta en estado {0} no se le envía nada")
    @EnumSource(value = EstadoCuenta.class, names = "PENDIENTE_VERIFICACION", mode = EnumSource.Mode.EXCLUDE)
    void aUnaCuentaYaVerificadaNoSeLeEnvia(EstadoCuenta estado) {
        Usuario cuenta = cuentaEn(estado);

        reenviar.ejecutar(cuenta.id());

        assertThat(puertos.enviador.enviados).isEmpty();
        assertThat(puertos.llamadas).containsExactly("buscarCuenta (fuera)");
    }

    @Test
    @DisplayName("si la cuenta ya no existe, no se envía nada ni hay error")
    void siLaCuentaNoExisteNoPasaNada() {
        reenviar.ejecutar(UUID.randomUUID());

        assertThat(puertos.enviador.enviados).isEmpty();
        assertThat(puertos.llamadas).containsExactly("buscarCuenta (fuera)");
    }

    @Test
    @DisplayName("con 3 enlaces en la última hora se rechaza con el tiempo que falta para el siguiente")
    void conElTopeAlcanzadoSeRechaza() {
        Usuario cuenta = cuentaEn(EstadoCuenta.PENDIENTE_VERIFICACION);
        emitidoHace(cuenta, Duration.ofMinutes(50));
        emitidoHace(cuenta, Duration.ofMinutes(30));
        emitidoHace(cuenta, Duration.ofMinutes(10));

        DemasiadosEnvios error = assertThrows(DemasiadosEnvios.class, () -> reenviar.ejecutar(cuenta.id()));

        assertThat(error.espera()).isEqualTo(Duration.ofMinutes(10));
        assertThat(puertos.enviador.enviados).isEmpty();
        assertThat(puertos.tokens.guardados).hasSize(3);
    }

    @Test
    @DisplayName("los enlaces de hace más de una hora no cuentan para el tope")
    void losEnlacesAntiguosNoCuentan() {
        Usuario cuenta = cuentaEn(EstadoCuenta.PENDIENTE_VERIFICACION);
        emitidoHace(cuenta, Duration.ofMinutes(61));
        emitidoHace(cuenta, Duration.ofMinutes(30));
        emitidoHace(cuenta, Duration.ofMinutes(10));

        reenviar.ejecutar(cuenta.id());

        assertThat(puertos.enviador.enviados).hasSize(1);
    }

    @Test
    @DisplayName("tres reenvíos seguidos se aceptan y el cuarto se rechaza")
    void elCuartoSeRechaza() {
        Usuario cuenta = cuentaEn(EstadoCuenta.PENDIENTE_VERIFICACION);

        reenviar.ejecutar(cuenta.id());
        reenviar.ejecutar(cuenta.id());
        reenviar.ejecutar(cuenta.id());

        assertThrows(DemasiadosEnvios.class, () -> reenviar.ejecutar(cuenta.id()));
        assertThat(puertos.enviador.enviados).hasSize(3);
    }

    // ---------- Ayudas ----------

    private Usuario cuentaEn(EstadoCuenta estado) {
        Usuario cuenta = new Usuario(UUID.randomUUID(), "Ana", "ana@correo.com",
                new CorreoNormalizado("ana@correo.com"), "hash", Rol.USUARIO, estado);
        puertos.usuarios.agregar(cuenta);
        return cuenta;
    }

    private void emitidoHace(Usuario cuenta, Duration hace) {
        puertos.tokens.guardados.add(TokenVerificacion.nuevo(
                cuenta.id(), PuertosFalsos.sha256(UUID.randomUUID().toString()), AHORA.minus(hace)));
    }
}