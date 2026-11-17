package com.smartkash.identidad.sesion.aplicacion;

import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.sesion.dominio.TokenAcceso;

import java.time.Instant;
import java.util.Objects;

/**
 * Resultado de un inicio de sesión correcto.
 *
 * <p>De dónde vienen los datos: del caso de uso {@link IniciarSesion}. A dónde van: a la
 * capa web, que pone el token de acceso y los datos de la cuenta en el cuerpo de la
 * respuesta y el token de renovación en una cookie.</p>
 *
 * <p>Es el único sitio donde existe el valor en claro del token de renovación: el servicio
 * solo guarda su huella, así que si se pierde este objeto, ese valor no se puede
 * recuperar.</p>
 *
 * @param tokenAcceso        el token de acceso firmado y su caducidad
 * @param tokenRenovacion    el valor en claro del token de renovación, para la cookie
 * @param renovacionExpiraEn cuándo caduca el token de renovación
 * @param requiereAceptarPolitica {@code true} si se publicó una política de tratamiento que
 *                           cambia la finalidad y la persona aún no la aceptó; la sesión se
 *                           inicia igual y el frontend le pide aceptarla
 * @param usuario            la cuenta que inició sesión
 */
public record SesionIniciada(
        TokenAcceso tokenAcceso,
        String tokenRenovacion,
        Instant renovacionExpiraEn,
        boolean requiereAceptarPolitica,
        Usuario usuario) {

    /**
     * Comprueba que no falta ningún dato.
     *
     * @throws NullPointerException si falta alguno
     */
    public SesionIniciada {
        Objects.requireNonNull(tokenAcceso, "El token de acceso es obligatorio.");
        Objects.requireNonNull(tokenRenovacion, "El token de renovación es obligatorio.");
        Objects.requireNonNull(renovacionExpiraEn, "La caducidad de la renovación es obligatoria.");
        Objects.requireNonNull(usuario, "La cuenta es obligatoria.");
    }

    /**
     * Devuelve un texto corto para depuración.
     *
     * <p>Se omite el valor del token de renovación: quien lo leyera en un log podría pedir
     * tokens de acceso en nombre de la persona durante días.</p>
     *
     * @return texto con el token de acceso (que ya oculta su valor), la caducidad y la cuenta
     */
    @Override
    public String toString() {
        return "SesionIniciada[tokenAcceso=" + tokenAcceso + ", renovacionExpiraEn="
                + renovacionExpiraEn + ", requiereAceptarPolitica=" + requiereAceptarPolitica
                + ", usuario=" + usuario + "]";
    }
}