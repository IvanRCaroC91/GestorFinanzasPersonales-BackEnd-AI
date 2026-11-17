package com.smartkash.identidad.verificacion.adaptadores.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la solicitud de verificación del correo, tal como llega en JSON.
 *
 * <p>De dónde viene: la página de la web lee el token del enlace (detrás de {@code #}) y lo
 * envía en este cuerpo. A dónde va: al caso de uso, sin transformar.</p>
 *
 * <p>Reglas de forma: el token es obligatorio y tiene entre 20 y 200 caracteres, como fija el
 * contrato. Los tokens que emite el servicio tienen 43; los límites holgados permiten cambiar
 * su tamaño sin cambiar el contrato. No lleva ningún identificador de cuenta: la cuenta sale
 * del token guardado, y el servicio rechaza cualquier campo de más.</p>
 *
 * @param token el valor que llegó en el enlace
 */
record VerificacionSolicitud(

        @NotNull(message = "El token es obligatorio.")
        @Size(min = 20, max = 200, message = "El token debe tener entre 20 y 200 caracteres.")
        String token) {

    /**
     * Devuelve un texto para depuración sin el token.
     *
     * @return un texto fijo
     */
    @Override
    public String toString() {
        return "VerificacionSolicitud[token oculto]";
    }
}