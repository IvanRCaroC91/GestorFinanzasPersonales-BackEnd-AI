package com.smartkash.identidad.sesion.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Token de acceso recién emitido, junto con el momento en que caduca.
 *
 * <p>Qué es un token de acceso: el texto firmado que la persona presenta en cada petición
 * para demostrar quién es, en la cabecera {@code Authorization}. Lleva dentro su propia
 * caducidad y una firma, así que el servidor no necesita guardarlo para comprobarlo.</p>
 *
 * <p>De dónde vienen los datos: de quien firma el token al terminar un inicio de sesión
 * correcto. A dónde van: al cuerpo de la respuesta. El servicio no lo guarda en ningún
 * sitio.</p>
 *
 * @param valor    el token firmado, tal como se entrega a la persona
 * @param expiraEn momento a partir del cual deja de valer
 */
public record TokenAcceso(String valor, Instant expiraEn) {

    /** Tiempo durante el que vale un token de acceso. */
    public static final Duration VIGENCIA = Duration.ofMinutes(15);

    /**
     * Comprueba que el token tiene valor y caducidad.
     *
     * @throws NullPointerException     si falta algún dato
     * @throws IllegalArgumentException si el valor está vacío
     */
    public TokenAcceso {
        Objects.requireNonNull(valor, "El valor del token es obligatorio.");
        Objects.requireNonNull(expiraEn, "El momento de caducidad es obligatorio.");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("El valor del token es obligatorio.");
        }
    }

    /**
     * Devuelve un texto corto para depuración.
     *
     * <p>Un record normalmente muestra todos sus campos. Aquí se omite el valor: quien lo
     * leyera en un log podría hacerse pasar por la persona hasta que caduque.</p>
     *
     * @return texto con la caducidad únicamente
     */
    @Override
    public String toString() {
        return "TokenAcceso[expiraEn=" + expiraEn + "]";
    }
}