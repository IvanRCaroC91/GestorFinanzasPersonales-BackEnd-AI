package com.smartkash.identidad.verificacion.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lo que el servicio recuerda de un enlace de verificación del correo.
 *
 * <p>Qué es: un token de un solo uso. Se envía por correo dentro de un enlace; quien lo presenta
 * demuestra que tiene acceso a ese buzón. Vale {@link #VIGENCIA} y una sola vez.</p>
 *
 * <p>Qué guarda y qué no: guarda la huella del valor, nunca el valor. Quien lea la tabla no
 * obtiene nada que pueda usar como enlace. Para que nadie guarde el valor por error, esta clase
 * solo acepta textos con forma de huella.</p>
 *
 * <p>Qué es un record inmutable aquí: usar el token no lo modifica; {@link #usar} devuelve otro
 * con el momento de uso anotado. Así no puede quedar a medio cambiar.</p>
 *
 * <p>De dónde vienen los datos: de los casos de uso que emiten enlaces (registro y reenvío) y
 * de la tabla de tokens de un solo uso. A dónde van: a esa tabla.</p>
 *
 * @param id        identificador del token
 * @param usuarioId cuenta cuyo correo se verifica
 * @param huella    huella del valor enviado por correo: 64 cifras hexadecimales en minúsculas
 * @param creadoEn  momento en que se emitió
 * @param expiraEn  momento a partir del cual deja de valer
 * @param usadoEn   momento en que se usó, o {@code null} si todavía no se ha usado
 */
public record TokenVerificacion(
        UUID id,
        UUID usuarioId,
        String huella,
        Instant creadoEn,
        Instant expiraEn,
        Instant usadoEn) {

    /** Tiempo durante el que vale un enlace de verificación. */
    public static final Duration VIGENCIA = Duration.ofHours(24);

    private static final Pattern FORMA_DE_HUELLA = Pattern.compile("[0-9a-f]{64}");

    /**
     * Comprueba que los datos son coherentes al crear el objeto.
     *
     * @throws NullPointerException     si falta algún dato obligatorio
     * @throws IllegalArgumentException si la huella no tiene forma de huella o el token no
     *                                  caduca después de crearse
     */
    public TokenVerificacion {
        Objects.requireNonNull(id, "El identificador es obligatorio.");
        Objects.requireNonNull(usuarioId, "La cuenta es obligatoria.");
        Objects.requireNonNull(huella, "La huella es obligatoria.");
        Objects.requireNonNull(creadoEn, "El momento de creación es obligatorio.");
        Objects.requireNonNull(expiraEn, "El momento de caducidad es obligatorio.");

        // El mensaje no incluye el texto recibido: si fuera el valor en claro, acabaría en un log.
        if (!FORMA_DE_HUELLA.matcher(huella).matches()) {
            throw new IllegalArgumentException(
                    "Se esperaba la huella del token: 64 cifras hexadecimales en minúsculas.");
        }
        if (!expiraEn.isAfter(creadoEn)) {
            throw new IllegalArgumentException("El token debe caducar después de crearse.");
        }
    }

    /**
     * Crea el token de un enlace recién emitido.
     *
     * <p>El identificador se genera al azar y la caducidad es la hora de emisión más
     * {@link #VIGENCIA}. Nace sin usar.</p>
     *
     * @param usuarioId cuenta cuyo correo se verifica
     * @param huella    huella del valor que se envía en el enlace
     * @param ahora     momento de la emisión
     * @return el token, listo para guardarse
     * @throws IllegalArgumentException si la huella no tiene forma de huella
     */
    public static TokenVerificacion nuevo(UUID usuarioId, String huella, Instant ahora) {
        Objects.requireNonNull(ahora, "El momento de emisión es obligatorio.");
        return new TokenVerificacion(UUID.randomUUID(), usuarioId, huella, ahora, ahora.plus(VIGENCIA), null);
    }

    /**
     * Dice si el token todavía se puede usar: no se ha usado y no ha caducado.
     *
     * @param ahora momento de la consulta
     * @return {@code true} si se puede usar
     */
    public boolean utilizable(Instant ahora) {
        return usadoEn == null && ahora.isBefore(expiraEn);
    }

    /**
     * Usa el token y devuelve cómo queda.
     *
     * @param ahora momento del uso
     * @return otro token igual, con el momento de uso anotado; este no cambia
     * @throws EnlaceNoValido si ya se usó o ya caducó, con el mismo error en ambos casos
     */
    public TokenVerificacion usar(Instant ahora) {
        if (!utilizable(ahora)) {
            throw new EnlaceNoValido();
        }
        return new TokenVerificacion(id, usuarioId, huella, creadoEn, expiraEn, ahora);
    }

    /**
     * Devuelve un texto corto para depuración, sin la huella.
     *
     * @return texto con los identificadores y las fechas
     */
    @Override
    public String toString() {
        return "TokenVerificacion[id=" + id + ", usuarioId=" + usuarioId + ", creadoEn=" + creadoEn
                + ", expiraEn=" + expiraEn + ", usadoEn=" + usadoEn + "]";
    }
}