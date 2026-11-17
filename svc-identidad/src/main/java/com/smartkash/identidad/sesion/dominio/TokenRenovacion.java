package com.smartkash.identidad.sesion.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lo que el servicio recuerda de un token de renovación.
 *
 * <p>Qué es un token de renovación: un valor aleatorio que el navegador guarda en una
 * cookie y que permite pedir un token de acceso nuevo sin volver a escribir la contraseña.
 * Dura mucho más que el token de acceso: 7 días frente a 15 minutos.</p>
 *
 * <p>Qué guarda y qué no: guarda la huella del valor, nunca el valor. Así, quien lea la
 * tabla no obtiene nada que pueda presentar como si fuera la cookie. Para que nadie guarde
 * el valor por error, esta clase solo acepta textos con forma de huella.</p>
 *
 * <p>Qué es la familia: todos los tokens de renovación de una misma sesión (un inicio de
 * sesión en un dispositivo) comparten un identificador de familia. Cada inicio de sesión
 * abre una familia nueva. Servirá más adelante para cerrar la sesión entera de una vez.</p>
 *
 * <p>De dónde vienen los datos: del caso de uso de inicio de sesión, que genera el valor,
 * calcula su huella y aporta la hora. A dónde van: a la tabla de tokens de renovación.</p>
 *
 * @param id        identificador del token
 * @param familiaId identificador de la sesión a la que pertenece
 * @param usuarioId cuenta dueña de la sesión
 * @param huella    huella del valor que viaja en la cookie: 64 cifras hexadecimales en
 *                  minúsculas
 * @param emitidoEn momento en que se emitió
 * @param expiraEn  momento a partir del cual deja de valer
 */
public record TokenRenovacion(
        UUID id,
        UUID familiaId,
        UUID usuarioId,
        String huella,
        Instant emitidoEn,
        Instant expiraEn) {

    /** Tiempo durante el que vale un token de renovación. */
    public static final Duration VIGENCIA = Duration.ofDays(7);

    private static final Pattern FORMA_DE_HUELLA = Pattern.compile("[0-9a-f]{64}");

    /**
     * Comprueba que los datos son coherentes al crear el objeto.
     *
     * @throws NullPointerException     si falta algún dato
     * @throws IllegalArgumentException si la huella no tiene forma de huella o si el token
     *                                  no caduca después de emitirse
     */
    public TokenRenovacion {
        Objects.requireNonNull(id, "El identificador es obligatorio.");
        Objects.requireNonNull(familiaId, "La familia es obligatoria.");
        Objects.requireNonNull(usuarioId, "El dueño es obligatorio.");
        Objects.requireNonNull(huella, "La huella es obligatoria.");
        Objects.requireNonNull(emitidoEn, "El momento de emisión es obligatorio.");
        Objects.requireNonNull(expiraEn, "El momento de caducidad es obligatorio.");

        // El mensaje no incluye el texto recibido: si fuera el valor en claro, acabaría en
        // un log justo lo que esta comprobación quiere proteger.
        if (!FORMA_DE_HUELLA.matcher(huella).matches()) {
            throw new IllegalArgumentException(
                    "Se esperaba la huella del token: 64 cifras hexadecimales en minúsculas.");
        }
        if (!expiraEn.isAfter(emitidoEn)) {
            throw new IllegalArgumentException("El token debe caducar después de emitirse.");
        }
    }

    /**
     * Crea el token de renovación de una persona que acaba de iniciar sesión.
     *
     * <p>El identificador y la familia se generan aquí al azar, y la caducidad se calcula
     * sumando {@link #VIGENCIA} a la hora de emisión. Quien llama no puede elegirlos.</p>
     *
     * @param usuarioId cuenta que inició sesión
     * @param huella    huella del valor que se entrega en la cookie
     * @param ahora     momento del inicio de sesión
     * @return el token, listo para guardarse
     * @throws NullPointerException     si falta algún dato
     * @throws IllegalArgumentException si la huella no tiene forma de huella
     */
    public static TokenRenovacion deSesionNueva(UUID usuarioId, String huella, Instant ahora) {
        Objects.requireNonNull(ahora, "El momento de emisión es obligatorio.");
        return new TokenRenovacion(
                UUID.randomUUID(), UUID.randomUUID(), usuarioId, huella, ahora, ahora.plus(VIGENCIA));
    }

    /**
     * Dice si el token todavía vale por fecha.
     *
     * @param ahora momento de la consulta
     * @return {@code true} si aún no ha llegado el momento de caducidad
     */
    public boolean vigente(Instant ahora) {
        return ahora.isBefore(expiraEn);
    }

    /**
     * Devuelve un texto corto para depuración.
     *
     * <p>Un record normalmente muestra todos sus campos. Aquí se omite la huella para que
     * no termine escrita en un log por accidente.</p>
     *
     * @return texto con los identificadores y las fechas
     */
    @Override
    public String toString() {
        return "TokenRenovacion[id=" + id + ", familiaId=" + familiaId + ", usuarioId=" + usuarioId
                + ", emitidoEn=" + emitidoEn + ", expiraEn=" + expiraEn + "]";
    }
}