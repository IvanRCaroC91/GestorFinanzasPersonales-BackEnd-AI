package com.smartkash.identidad.sesion.adaptadores.web;

import com.smartkash.identidad.cuenta.dominio.EstadoCuenta;
import com.smartkash.identidad.cuenta.dominio.Rol;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.sesion.aplicacion.SesionIniciada;

import java.time.Instant;
import java.util.UUID;

/**
 * Cuerpo de la respuesta de un inicio de sesión correcto, tal como sale en JSON.
 *
 * <p>De dónde vienen los datos: de {@link SesionIniciada}, mediante {@link #desde}. A dónde
 * van: al frontend, que guarda el token de acceso en memoria y lo envía en cada petición.</p>
 *
 * <p>Qué no lleva: el token de renovación, que viaja solo en una cookie que el código de la
 * página no puede leer; ni el hash de la contraseña.</p>
 *
 * @param tokenAcceso             el token firmado
 * @param tipoToken               siempre {@code Bearer}: cómo se envía,
 *                                {@code Authorization: Bearer <token>}
 * @param expiraEn                cuándo caduca el token de acceso
 * @param requiereAceptarPolitica si la persona debe aceptar una política nueva
 * @param eliminacionProgramadaEn cuándo se borrará la cuenta, o {@code null}
 * @param usuario                 los datos de la cuenta
 */
record SesionRespuesta(
        String tokenAcceso,
        String tipoToken,
        Instant expiraEn,
        boolean requiereAceptarPolitica,
        Instant eliminacionProgramadaEn,
        UsuarioRespuesta usuario) {

    /**
     * Arma la respuesta a partir del resultado del caso de uso.
     *
     * @param sesion el resultado del inicio de sesión
     * @return el cuerpo de la respuesta
     */
    static SesionRespuesta desde(SesionIniciada sesion) {
        Usuario cuenta = sesion.usuario();
        return new SesionRespuesta(
                sesion.tokenAcceso().valor(),
                "Bearer",
                sesion.tokenAcceso().expiraEn(),
                sesion.requiereAceptarPolitica(),
                cuenta.eliminacionProgramadaEn(),
                new UsuarioRespuesta(cuenta.id(), cuenta.nombre(), cuenta.correoOriginal(),
                        cuenta.rol(), cuenta.estado()));
    }

    /**
     * Devuelve un texto para depuración sin el token.
     *
     * @return texto con la caducidad y la cuenta
     */
    @Override
    public String toString() {
        return "SesionRespuesta[expiraEn=" + expiraEn + ", usuario=" + usuario + "]";
    }

    /**
     * Datos de la cuenta que se devuelven al iniciar sesión.
     *
     * @param id     identificador de la cuenta
     * @param nombre nombre para mostrar
     * @param correo correo tal como lo escribió la persona al registrarse
     * @param rol    qué puede hacer la cuenta
     * @param estado en qué punto de su vida está la cuenta
     */
    record UsuarioRespuesta(UUID id, String nombre, String correo, Rol rol, EstadoCuenta estado) {

        /**
         * Devuelve un texto para depuración sin datos personales.
         *
         * @return texto con el identificador, el rol y el estado
         */
        @Override
        public String toString() {
            return "UsuarioRespuesta[id=" + id + ", rol=" + rol + ", estado=" + estado + "]";
        }
    }
}