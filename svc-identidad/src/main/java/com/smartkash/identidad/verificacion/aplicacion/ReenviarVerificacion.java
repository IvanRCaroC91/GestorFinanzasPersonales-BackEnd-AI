package com.smartkash.identidad.verificacion.aplicacion;

import com.smartkash.identidad.cuenta.dominio.EstadoCuenta;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.verificacion.dominio.DemasiadosEnvios;
import com.smartkash.identidad.verificacion.dominio.TopeDeEnvios;
import com.smartkash.identidad.verificacion.puertos.RepositorioTokensDeUnUso;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso: enviar otra vez el enlace de verificación a quien lo pide.
 *
 * <p>Quién es el dueño: la cuenta llega como identificador sacado del token de acceso de quien
 * pide el reenvío. Nunca del cuerpo de la petición, así que nadie puede pedir enlaces para otra
 * cuenta.</p>
 *
 * <p>Qué hace:</p>
 * <ol>
 *   <li>Busca la cuenta. Si ya no existe o ya no está pendiente de verificar, termina sin hacer
 *       nada y sin error: la respuesta es la misma en todos los casos.</li>
 *   <li>Comprueba el tope de {@value TopeDeEnvios#MAXIMO} enlaces por hora. Si se alcanzó, lanza
 *       {@link DemasiadosEnvios} con el tiempo que falta.</li>
 *   <li>Emite un enlace nuevo con {@link EmitirEnlaceDeVerificacion}, que invalida los
 *       anteriores y envía el correo.</li>
 * </ol>
 *
 * <p>Límite conocido: si llegan dos peticiones exactamente a la vez con dos enlaces ya
 * emitidos, las dos pueden pasar la comprobación del tope y quedar cuatro. El tope frena el
 * abuso continuado, que es su objetivo; no se bloquea la cuenta para evitar ese caso.</p>
 */
public class ReenviarVerificacion {

    private final RepositorioUsuarios usuarios;
    private final RepositorioTokensDeUnUso tokens;
    private final EmitirEnlaceDeVerificacion emitirEnlace;
    private final Clock reloj;

    /**
     * Crea el caso de uso con todo lo que necesita del exterior.
     *
     * @param usuarios     dónde se busca la cuenta
     * @param tokens       dónde se consultan los enlaces ya emitidos
     * @param emitirEnlace quién emite y envía el enlace nuevo
     * @param reloj        de dónde se toma la hora
     * @throws NullPointerException si falta alguno
     */
    public ReenviarVerificacion(
            RepositorioUsuarios usuarios,
            RepositorioTokensDeUnUso tokens,
            EmitirEnlaceDeVerificacion emitirEnlace,
            Clock reloj) {
        this.usuarios = Objects.requireNonNull(usuarios);
        this.tokens = Objects.requireNonNull(tokens);
        this.emitirEnlace = Objects.requireNonNull(emitirEnlace);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /**
     * Reenvía el enlace a la cuenta, si está pendiente de verificar.
     *
     * @param usuarioId la cuenta, sacada del token de acceso
     * @throws DemasiadosEnvios si ya se emitieron {@value TopeDeEnvios#MAXIMO} enlaces en la
     *                          última hora
     */
    public void ejecutar(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "La cuenta es obligatoria.");
        Optional<Usuario> cuenta = usuarios.buscarPorId(usuarioId);
        if (cuenta.isEmpty() || cuenta.get().estado() != EstadoCuenta.PENDIENTE_VERIFICACION) {
            return;
        }

        Instant ahora = reloj.instant();
        List<Instant> emitidos = tokens.emitidosDesde(usuarioId, TopeDeEnvios.inicioDeLaVentana(ahora));
        if (!TopeDeEnvios.admiteOtro(emitidos.size())) {
            throw new DemasiadosEnvios(TopeDeEnvios.esperaHastaOtro(emitidos, ahora));
        }

        emitirEnlace.emitir(cuenta.get());
    }
}