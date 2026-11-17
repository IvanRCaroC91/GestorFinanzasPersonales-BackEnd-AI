package com.smartkash.identidad.verificacion.aplicacion;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.tokens.puertos.CalculadorHuellas;
import com.smartkash.identidad.verificacion.dominio.EnlaceNoValido;
import com.smartkash.identidad.verificacion.dominio.TokenVerificacion;
import com.smartkash.identidad.verificacion.puertos.RepositorioTokensDeUnUso;
import com.smartkash.identidad.verificacion.puertos.UnidadDeTrabajo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del caso de uso {@link VerificarCorreo}.
 *
 * <p>Qué comprueban: que un enlace válido marca el token como usado y activa la cuenta, las dos
 * cosas dentro de la misma unidad de trabajo; y que un enlace inexistente, caducado, ya usado o
 * usado a la vez por otra petición produce siempre el mismo error sin tocar la cuenta.</p>
 *
 * <p>Cómo lo hacen: con puertos falsos en memoria, definidos al final del archivo. La unidad de
 * trabajo falsa anota si una operación ocurrió dentro o fuera de ella, para demostrar que las
 * escrituras van juntas.</p>
 */
class VerificarCorreoTest {

    private static final Instant AHORA = Instant.parse("2026-11-17T10:00:00Z");
    private static final String VALOR = "valor-del-enlace-de-prueba-0123456789abcdef";

    private final UnidadFalsa unidad = new UnidadFalsa();
    private final TokensEnMemoria tokens = new TokensEnMemoria(unidad);
    private final CuentasFalsas cuentas = new CuentasFalsas(unidad);
    private final HuellasFalsas huellas = new HuellasFalsas();

    private final VerificarCorreo verificarCorreo = new VerificarCorreo(
            tokens, cuentas, huellas, unidad, Clock.fixed(AHORA, ZoneOffset.UTC));

    private final UUID cuenta = UUID.randomUUID();

    // ---------- Enlace válido ----------

    @Test
    @DisplayName("un enlace válido marca el token como usado y activa la cuenta")
    void unEnlaceValidoActivaLaCuenta() {
        TokenVerificacion token = emitido(AHORA.minus(Duration.ofHours(2)));

        verificarCorreo.ejecutar(VALOR);

        assertThat(tokens.marcados).containsExactly(token.usar(AHORA));
        assertThat(cuentas.verificadas).containsExactly(new Verificacion(cuenta, AHORA));
    }

    @Test
    @DisplayName("marcar el token y activar la cuenta ocurren dentro de la misma unidad de trabajo")
    void lasDosEscriturasVanJuntas() {
        emitido(AHORA.minus(Duration.ofHours(2)));

        verificarCorreo.ejecutar(VALOR);

        assertThat(unidad.veces).isEqualTo(1);
        assertThat(tokens.marcadosFueraDeLaUnidad).isZero();
        assertThat(cuentas.verificadasFueraDeLaUnidad).isZero();
    }

    @Test
    @DisplayName("el token se busca por la huella del valor, nunca por el valor")
    void seBuscaPorLaHuella() {
        emitido(AHORA.minus(Duration.ofHours(2)));

        verificarCorreo.ejecutar(VALOR);

        assertThat(huellas.textos).containsExactly(VALOR);
        assertThat(tokens.huellasBuscadas).containsExactly(sha256(VALOR));
    }

    // ---------- Enlaces que no sirven ----------

    @Test
    @DisplayName("un enlace que no existe no toca ninguna cuenta")
    void unEnlaceInexistenteNoTocaNada() {
        EnlaceNoValido error = assertThrows(EnlaceNoValido.class, () -> verificarCorreo.ejecutar(VALOR));

        assertThat(error.getMessage()).isEqualTo(EnlaceNoValido.MENSAJE);
        assertThat(tokens.marcados).isEmpty();
        assertThat(cuentas.verificadas).isEmpty();
    }

    @Test
    @DisplayName("un enlace caducado no se marca ni activa la cuenta")
    void unEnlaceCaducadoNoTocaNada() {
        emitido(AHORA.minus(Duration.ofHours(25)));

        assertThrows(EnlaceNoValido.class, () -> verificarCorreo.ejecutar(VALOR));

        assertThat(tokens.marcados).isEmpty();
        assertThat(cuentas.verificadas).isEmpty();
    }

    @Test
    @DisplayName("un enlace ya usado no vuelve a activar la cuenta")
    void unEnlaceUsadoNoTocaNada() {
        TokenVerificacion token = emitido(AHORA.minus(Duration.ofHours(2)));
        tokens.porHuella.put(token.huella(), token.usar(AHORA.minus(Duration.ofHours(1))));

        assertThrows(EnlaceNoValido.class, () -> verificarCorreo.ejecutar(VALOR));

        assertThat(tokens.marcados).isEmpty();
        assertThat(cuentas.verificadas).isEmpty();
    }

    @Test
    @DisplayName("si otra petición usó el enlace en el mismo instante, este intento no activa la cuenta")
    void siOtraPeticionLoUsoAntesNoActiva() {
        emitido(AHORA.minus(Duration.ofHours(2)));
        tokens.otraPeticionGanaLaCarrera = true;

        assertThrows(EnlaceNoValido.class, () -> verificarCorreo.ejecutar(VALOR));

        assertThat(cuentas.verificadas).isEmpty();
    }

    @Test
    @DisplayName("inexistente, caducado y usado dan el mismo error con el mismo mensaje")
    void todosLosMotivosDanElMismoError() {
        String porInexistente = assertThrows(EnlaceNoValido.class, () -> verificarCorreo.ejecutar(VALOR)).getMessage();

        emitido(AHORA.minus(Duration.ofHours(25)));
        String porCaducado = assertThrows(EnlaceNoValido.class, () -> verificarCorreo.ejecutar(VALOR)).getMessage();

        TokenVerificacion token = emitido(AHORA.minus(Duration.ofHours(2)));
        tokens.porHuella.put(token.huella(), token.usar(AHORA.minusSeconds(60)));
        String porUsado = assertThrows(EnlaceNoValido.class, () -> verificarCorreo.ejecutar(VALOR)).getMessage();

        assertThat(porCaducado).isEqualTo(porInexistente);
        assertThat(porUsado).isEqualTo(porInexistente);
    }

    // ---------- Ayudas ----------

    /* Guarda un token emitido en ese momento para el valor de prueba. */
    private TokenVerificacion emitido(Instant creadoEn) {
        TokenVerificacion token = TokenVerificacion.nuevo(cuenta, sha256(VALOR), creadoEn);
        tokens.porHuella.put(token.huella(), token);
        return token;
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- Puertos falsos ----------

    /** Una cuenta marcada como verificada y en qué momento. */
    private record Verificacion(UUID usuarioId, Instant momento) {
    }

    /** Ejecuta el trabajo directamente y deja saber si se está dentro de él. */
    private static final class UnidadFalsa implements UnidadDeTrabajo {

        boolean dentro;
        int veces;

        @Override
        public void ejecutar(Runnable trabajo) {
            veces++;
            dentro = true;
            try {
                trabajo.run();
            } finally {
                dentro = false;
            }
        }
    }

    /** Tokens en un mapa por huella. Puede simular que otra petición lo marcó antes. */
    private static final class TokensEnMemoria implements RepositorioTokensDeUnUso {

        private final UnidadFalsa unidad;
        final Map<String, TokenVerificacion> porHuella = new HashMap<>();
        final List<String> huellasBuscadas = new ArrayList<>();
        final List<TokenVerificacion> marcados = new ArrayList<>();
        int marcadosFueraDeLaUnidad;
        boolean otraPeticionGanaLaCarrera;

        TokensEnMemoria(UnidadFalsa unidad) {
            this.unidad = unidad;
        }

        @Override
        public Optional<TokenVerificacion> buscarPorHuella(String huella) {
            huellasBuscadas.add(huella);
            return Optional.ofNullable(porHuella.get(huella));
        }

        @Override
        public boolean marcarComoUsado(TokenVerificacion usado) {
            if (!unidad.dentro) {
                marcadosFueraDeLaUnidad++;
            }
            if (otraPeticionGanaLaCarrera) {
                return false;
            }
            marcados.add(usado);
            return true;
        }

        @Override
        public void guardar(TokenVerificacion token) {
            throw new UnsupportedOperationException("La verificación no lo usa.");
        }

        @Override
        public List<Instant> emitidosDesde(UUID usuarioId, Instant desde) {
            throw new UnsupportedOperationException("La verificación no lo usa.");
        }

        @Override
        public void invalidarPendientes(UUID usuarioId, Instant ahora) {
            throw new UnsupportedOperationException("La verificación no lo usa.");
        }
    }

    /** Repositorio de cuentas que solo apunta las verificaciones. */
    private static final class CuentasFalsas implements RepositorioUsuarios {

        private final UnidadFalsa unidad;
        final List<Verificacion> verificadas = new ArrayList<>();
        int verificadasFueraDeLaUnidad;

        CuentasFalsas(UnidadFalsa unidad) {
            this.unidad = unidad;
        }

        @Override
        public void marcarVerificada(UUID usuarioId, Instant ahora) {
            if (!unidad.dentro) {
                verificadasFueraDeLaUnidad++;
            }
            verificadas.add(new Verificacion(usuarioId, ahora));
        }

        @Override
        public boolean existeConCorreo(CorreoNormalizado correo) {
            throw new UnsupportedOperationException("La verificación no lo usa.");
        }

        @Override
        public Optional<Usuario> buscarPorId(UUID usuarioId) {
            throw new UnsupportedOperationException("La verificación no lo usa.");
        }

        @Override
        public Optional<Usuario> buscarPorCorreo(CorreoNormalizado correo) {
            throw new UnsupportedOperationException("La verificación no lo usa.");
        }

        @Override
        public void guardar(Usuario usuario) {
            throw new UnsupportedOperationException("La verificación no lo usa.");
        }
    }

    /** Calculadora que apunta lo que recibe y devuelve su SHA-256 real. */
    private static final class HuellasFalsas implements CalculadorHuellas {

        final List<String> textos = new ArrayList<>();

        @Override
        public String huellaDe(String texto) {
            textos.add(texto);
            return sha256(texto);
        }
    }
}