package com.smartkash.identidad.sesion.adaptadores.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la solicitud de inicio de sesión, tal como llega en JSON.
 *
 * <p>De dónde vienen los datos: del formulario de inicio de sesión del frontend. A dónde van:
 * al caso de uso, sin transformar; el correo lo normaliza el dominio.</p>
 *
 * <p>Reglas de forma: el correo es obligatorio, con forma de correo y como mucho 254
 * caracteres; la contraseña es obligatoria y no puede estar vacía. El servicio rechaza
 * cualquier campo que no esté declarado aquí. Una solicitud mal formada se rechaza con 400
 * antes de llegar al caso de uso, así que no cuenta como intento fallido.</p>
 *
 * <p>Por qué la contraseña admite hasta 128 unidades: el contrato fija 64 caracteres como los
 * ve la persona, pero Java cuenta un emoji como dos unidades. El doble garantiza no rechazar
 * nunca una contraseña válida y aun así frena valores desmesurados antes de calcular un hash.</p>
 *
 * @param correo     correo como lo escribió la persona
 * @param contrasena contraseña en claro; solo se recibe, nunca se devuelve ni se registra
 */
record InicioSesionSolicitud(

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 254, message = "El correo no puede tener más de 254 caracteres.")
        String correo,

        @NotNull(message = "La contraseña es obligatoria.")
        @Size(min = 1, max = 128, message = "La contraseña debe tener entre 1 y 64 caracteres.")
        String contrasena) {

    /**
     * Devuelve un texto para depuración sin el correo ni la contraseña.
     *
     * @return un texto fijo
     */
    @Override
    public String toString() {
        return "InicioSesionSolicitud[datos ocultos]";
    }
}