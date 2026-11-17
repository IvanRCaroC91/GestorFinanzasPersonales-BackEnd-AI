package com.smartkash.identidad.verificacion.puertos;

import com.smartkash.identidad.verificacion.dominio.TokenVerificacion;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lugar donde se guardan los tokens de los enlaces de verificación del correo.
 *
 * <p>Qué es: un puerto. Los casos de uso de verificación y reenvío solo conocen estas
 * operaciones; detrás hay una tabla de tokens de un solo uso, que más adelante también guardará
 * los de recuperación de contraseña. Todas las operaciones de esta interfaz se refieren solo a
 * tokens de verificación del correo.</p>
 *
 * <p>Regla común: los tokens se identifican por su huella; el valor en claro nunca llega
 * aquí.</p>
 */
public interface RepositorioTokensDeUnUso {

    /**
     * Guarda un token recién emitido.
     *
     * @param token el token, con la huella del valor
     */
    void guardar(TokenVerificacion token);

    /**
     * Busca el token que corresponde a una huella.
     *
     * @param huella huella del valor que llegó en el enlace
     * @return el token, o una caja vacía si no existe
     */
    Optional<TokenVerificacion> buscarPorHuella(String huella);

    /**
     * Anota que un token se usó, solo si todavía estaba sin usar.
     *
     * <p>Por qué devuelve un resultado: si dos peticiones presentan el mismo enlace a la vez,
     * las dos lo encontrarán sin usar. La anotación debe ser una sola operación que compruebe y
     * escriba a la vez, de modo que solo una de ellas consiga marcarlo; la otra recibe
     * {@code false} y debe tratar el enlace como no válido.</p>
     *
     * @param usado el token con el momento de uso ya anotado por {@link TokenVerificacion#usar}
     * @return {@code true} si esta llamada lo marcó; {@code false} si ya estaba usado
     */
    boolean marcarComoUsado(TokenVerificacion usado);

    /**
     * Cuenta los tokens emitidos a una cuenta desde un momento dado.
     *
     * <p>Sirve para aplicar el tope de envíos por hora. Cuenta todos los emitidos, se hayan
     * usado, invalidado o no.</p>
     *
     * @param usuarioId la cuenta
     * @param desde     momento desde el que se cuenta
     * @return cuántos se emitieron
     */
    int contarEmitidosDesde(UUID usuarioId, Instant desde);

    /**
     * Hace que dejen de valer los tokens de una cuenta que siguen sin usar.
     *
     * <p>Se usa al emitir un enlace nuevo: solo el último enlace enviado debe funcionar.</p>
     *
     * @param usuarioId la cuenta
     * @param ahora     momento a partir del cual dejan de valer
     */
    void invalidarPendientes(UUID usuarioId, Instant ahora);
}