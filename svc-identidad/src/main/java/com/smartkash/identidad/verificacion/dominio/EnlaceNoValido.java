package com.smartkash.identidad.verificacion.dominio;

/**
 * Error de un enlace de verificación que no sirve.
 *
 * <p>Cuándo se lanza: cuando el token del enlace no existe, ya caducó o ya se usó. Los tres
 * casos reciben el mismo error con el mismo mensaje: distinguirlos solo ayudaría a quien prueba
 * tokens al azar a saber cuándo ha dado con uno que existió.</p>
 *
 * <p>A dónde va: la capa web lo convierte en un {@code 400} de regla de negocio con el código
 * {@value #CODIGO}. La persona puede iniciar sesión y pedir un enlace nuevo.</p>
 */
public final class EnlaceNoValido extends RuntimeException {

    /** Mensaje único para cualquier motivo. */
    public static final String MENSAJE =
            "El enlace de verificación no es válido o ha caducado. Inicia sesión y pide uno nuevo.";

    /** Código que identifica este error en la respuesta, para que la pantalla reaccione. */
    public static final String CODIGO = "ENLACE_NO_VALIDO";

    /**
     * Crea el error con el mensaje genérico.
     */
    public EnlaceNoValido() {
        super(MENSAJE);
    }
}
