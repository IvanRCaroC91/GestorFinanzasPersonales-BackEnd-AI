package com.smartkash.identidad.registro.dominio;

import java.util.Objects;

/**
 * Error que indica que un dato enviado para registrarse no cumple una regla de negocio.
 *
 * <p>Qué hace: además del mensaje para personas, lleva dos datos pensados para el
 * programa: el {@link Motivo} (qué regla se incumplió) y el campo de la solicitud que
 * tiene el problema. Con ellos, quien reciba el error puede decidir qué responder sin
 * tener que leer el texto del mensaje.</p>
 *
 * <p>De dónde vienen los datos: lo lanzan las reglas del registro, como la política de
 * contraseñas. A dónde van: la capa web lo convertirá en una respuesta de error que
 * lista los campos inválidos.</p>
 *
 * <p>Reglas: el mensaje nunca debe incluir el valor que escribió la persona (contraseña,
 * correo), porque los mensajes de error suelen terminar en los logs.</p>
 *
 * <p>Es una excepción no comprobada (hereda de {@link RuntimeException}): Java no obliga
 * a declararla ni a capturarla en cada método por el que pasa.</p>
 */
public class ErrorDeRegistro extends RuntimeException {

    /**
     * Lista cerrada de las reglas que puede incumplir un dato del registro.
     *
     * <p>Cada valor es un nombre fijo que el programa puede comparar con seguridad, a
     * diferencia de un texto libre, donde un error al escribir pasaría inadvertido.</p>
     */
    public enum Motivo {

        /** No se envió nombre, o llegó vacío o hecho solo de espacios. */
        NOMBRE_OBLIGATORIO,

        /** El nombre tiene más caracteres que el máximo. */
        NOMBRE_MUY_LARGO,

        /** No se envió contraseña, o llegó vacía. */
        CONTRASENA_OBLIGATORIA,

        /** La contraseña tiene menos caracteres que el mínimo. */
        CONTRASENA_MUY_CORTA,

        /** La contraseña tiene más caracteres que el máximo. */
        CONTRASENA_MUY_LARGA,

        /** La contraseña ocupa más bytes que el máximo, aunque su largo en caracteres sea válido. */
        CONTRASENA_SUPERA_BYTES,

        /** La contraseña es igual al correo de la persona, sin distinguir mayúsculas. */
        CONTRASENA_IGUAL_AL_CORREO,

        /** La contraseña está en la lista de contraseñas comunes. */
        CONTRASENA_COMUN,

        /** La persona no aceptó la política de tratamiento de datos, que es obligatoria. */
        POLITICA_NO_ACEPTADA,

        /** La versión de la política que envió la persona falta o no es la vigente. */
        VERSION_POLITICA_NO_VIGENTE
    }

    /** Qué regla del registro se incumplió. */
    private final Motivo motivo;

    /** Nombre del campo de la solicitud que causó el error. */
    private final String campo;

    /**
     * Crea el error con todo lo necesario para explicarlo.
     *
     * @param motivo  la regla que se incumplió; obligatorio
     * @param campo   nombre del campo de la solicitud que tiene el problema, tal como
     *                aparece en el cuerpo de la petición (por ejemplo, {@code contrasena});
     *                obligatorio
     * @param mensaje explicación para personas; no debe contener el valor rechazado
     * @throws NullPointerException si el motivo o el campo son nulos
     */
    public ErrorDeRegistro(Motivo motivo, String campo, String mensaje) {
        super(mensaje);
        this.motivo = Objects.requireNonNull(motivo, "El motivo es obligatorio.");
        this.campo = Objects.requireNonNull(campo, "El campo es obligatorio.");
    }

    /**
     * Devuelve la regla que se incumplió.
     *
     * @return el motivo del error; nunca nulo
     */
    public Motivo motivo() {
        return motivo;
    }

    /**
     * Devuelve el nombre del campo de la solicitud que tiene el problema.
     *
     * @return el nombre del campo; nunca nulo
     */
    public String campo() {
        return campo;
    }
}
