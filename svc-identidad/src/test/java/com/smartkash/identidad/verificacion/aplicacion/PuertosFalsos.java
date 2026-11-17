package com.smartkash.identidad.verificacion.aplicacion;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.tokens.puertos.CalculadorHuellas;
import com.smartkash.identidad.tokens.puertos.GeneradorTokens;
import com.smartkash.identidad.verificacion.dominio.CorreoDeVerificacion;
import com.smartkash.identidad.verificacion.dominio.TokenVerificacion;
import com.smartkash.identidad.verificacion.puertos.EnviadorCorreos;
import com.smartkash.identidad.verificacion.puertos.RepositorioTokensDeUnUso;
import com.smartkash.identidad.verificacion.puertos.UnidadDeTrabajo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Puertos falsos en memoria para las pruebas de la emisión y el reenvío de enlaces.
 *
 * <p>Todos apuntan lo que reciben en una lista común de llamadas, indicando si ocurrió dentro
 * o fuera de la unidad de trabajo. Así una prueba puede comprobar el orden de los pasos y qué
 * quedó dentro de la transacción.</p>
 */
final class PuertosFalsos {

    final List<String> llamadas = new ArrayList<>();
    final UnidadFalsa unidad = new UnidadFalsa();
    final TokensFalsos tokens = new TokensFalsos();
    final EnviadorFalso enviador = new EnviadorFalso();
    final GeneradorFalso generador = new GeneradorFalso();
    final HuellasFalsas huellas = new HuellasFalsas();
    final UsuariosFalsos usuarios = new UsuariosFalsos();

    static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void apuntar(String llamada) {
        llamadas.add(llamada + (unidad.dentro ? " (dentro)" : " (fuera)"));
    }

    /** Ejecuta el trabajo directamente y deja saber si se está dentro de él. */
    final class UnidadFalsa implements UnidadDeTrabajo {

        boolean dentro;

        @Override
        public void ejecutar(Runnable trabajo) {
            dentro = true;
            try {
                trabajo.run();
            } finally {
                dentro = false;
            }
        }
    }

    /** Tokens en una lista; se pueden sembrar emisiones anteriores. */
    final class TokensFalsos implements RepositorioTokensDeUnUso {

        final List<TokenVerificacion> guardados = new ArrayList<>();

        @Override
        public void guardar(TokenVerificacion token) {
            apuntar("guardarToken");
            guardados.add(token);
        }

        @Override
        public void invalidarPendientes(UUID usuarioId, Instant ahora) {
            apuntar("invalidarPendientes");
        }

        @Override
        public List<Instant> emitidosDesde(UUID usuarioId, Instant desde) {
            apuntar("consultarEmitidos");
            return guardados.stream()
                    .filter(token -> token.usuarioId().equals(usuarioId))
                    .map(TokenVerificacion::creadoEn)
                    .filter(creado -> !creado.isBefore(desde))
                    .sorted()
                    .toList();
        }

        @Override
        public Optional<TokenVerificacion> buscarPorHuella(String huella) {
            throw new UnsupportedOperationException("No se usa al emitir.");
        }

        @Override
        public boolean marcarComoUsado(TokenVerificacion usado) {
            throw new UnsupportedOperationException("No se usa al emitir.");
        }
    }

    /** Guarda los correos en una lista; puede simular que el servidor de correo falla. */
    final class EnviadorFalso implements EnviadorCorreos {

        final List<CorreoDeVerificacion> enviados = new ArrayList<>();
        boolean falla;

        @Override
        public void enviarVerificacion(CorreoDeVerificacion correo) {
            apuntar("enviarCorreo");
            if (falla) {
                throw new IllegalStateException("El servidor de correo no responde.");
            }
            enviados.add(correo);
        }
    }

    /** Devuelve valores numerados y recuerda el último. */
    static final class GeneradorFalso implements GeneradorTokens {

        private int generados;
        String ultimo;

        @Override
        public String generar() {
            generados++;
            ultimo = "valor-aleatorio-de-prueba-" + generados;
            return ultimo;
        }
    }

    /** Devuelve la SHA-256 real y apunta lo que recibe. */
    static final class HuellasFalsas implements CalculadorHuellas {

        final List<String> textos = new ArrayList<>();

        @Override
        public String huellaDe(String texto) {
            textos.add(texto);
            return sha256(texto);
        }
    }

    /** Cuentas en un mapa por identificador. */
    final class UsuariosFalsos implements RepositorioUsuarios {

        final Map<UUID, Usuario> cuentas = new HashMap<>();

        void agregar(Usuario cuenta) {
            cuentas.put(cuenta.id(), cuenta);
        }

        @Override
        public Optional<Usuario> buscarPorId(UUID usuarioId) {
            apuntar("buscarCuenta");
            return Optional.ofNullable(cuentas.get(usuarioId));
        }

        @Override
        public boolean existeConCorreo(CorreoNormalizado correo) {
            throw new UnsupportedOperationException("No se usa al reenviar.");
        }

        @Override
        public Optional<Usuario> buscarPorCorreo(CorreoNormalizado correo) {
            throw new UnsupportedOperationException("No se usa al reenviar.");
        }

        @Override
        public void guardar(Usuario usuario) {
            throw new UnsupportedOperationException("No se usa al reenviar.");
        }

        @Override
        public void marcarVerificada(UUID usuarioId, Instant ahora) {
            throw new UnsupportedOperationException("No se usa al reenviar.");
        }
    }
}