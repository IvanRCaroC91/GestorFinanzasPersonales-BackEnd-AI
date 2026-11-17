package com.smartkash.identidad.sesion.dominio;

/**
 * Error del inicio de sesión cuando no se puede dejar entrar a la persona.
 *
 * <p>Cuándo se lanza: siempre que las credenciales no sirven, sea cual sea el motivo real:
 * la contraseña no es la correcta, el correo no tiene cuenta o la cuenta no puede entrar.</p>
 *
 * <p>Por qué un solo error para todo: si el mensaje o el tipo de error cambiaran según el
 * motivo, cualquiera podría probar correos y averiguar cuáles tienen cuenta (lo que se
 * llama enumeración de usuarios). Por eso este error no lleva ningún dato: ni el correo,
 * ni el motivo, ni ninguna otra pista, y su mensaje es siempre el mismo.</p>
 *
 * <p>A dónde va: la capa web lo convierte en una respuesta {@code 401} genérica.</p>
 */
public final class CredencialesIncorrectas extends RuntimeException {

    /** Mensaje único, el mismo para cualquier motivo. */
    public static final String MENSAJE = "Correo o contraseña incorrectos.";

    /**
     * Crea el error con el mensaje genérico.
     */
    public CredencialesIncorrectas() {
        super(MENSAJE);
    }
}