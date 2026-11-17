package com.smartkash.identidad.verificacion.dominio;

import com.smartkash.identidad.cuenta.dominio.Usuario;

import java.util.Objects;

/**
 * Correo que se envía para que la persona confirme su dirección.
 *
 * <p>De dónde vienen los datos: de la cuenta (a quién y con qué nombre), de la configuración
 * (la dirección base de la web) y del valor recién generado del token. A dónde van: al
 * enviador de correos, que arma el mensaje y lo entrega.</p>
 *
 * <p>Forma del enlace: {@code <base>/verificar-correo#token=<valor>}. El token va detrás de
 * {@code #}, en lo que se llama fragmento. El navegador nunca envía el fragmento al servidor que
 * sirve la página, así que el token no queda escrito en sus registros; la página lo lee y lo
 * envía al servicio en el cuerpo de una petición.</p>
 *
 * <p>Este objeto contiene el valor en claro del token. Solo existe en memoria el tiempo justo
 * para enviarlo, y su texto de depuración no lo muestra.</p>
 *
 * @param destinatario dirección a la que se envía, tal como la escribió la persona
 * @param nombre       nombre de la persona, para el saludo
 * @param enlace       enlace completo con el token
 */
public record CorreoDeVerificacion(String destinatario, String nombre, String enlace) {

    /** Ruta de la página de la web que recibe el enlace. */
    public static final String RUTA_PAGINA = "/verificar-correo";

    /**
     * Comprueba que no falta ningún dato.
     *
     * @throws NullPointerException si falta alguno
     */
    public CorreoDeVerificacion {
        Objects.requireNonNull(destinatario, "El destinatario es obligatorio.");
        Objects.requireNonNull(nombre, "El nombre es obligatorio.");
        Objects.requireNonNull(enlace, "El enlace es obligatorio.");
    }

    /**
     * Arma el correo de verificación de una cuenta.
     *
     * @param cuenta        la cuenta cuyo correo se verifica
     * @param direccionBase dirección de la web, por ejemplo {@code https://app.ejemplo}; debe
     *                      empezar por {@code http://} o {@code https://}
     * @param valorToken    el valor en claro del token recién generado
     * @return el correo listo para enviarse
     * @throws IllegalArgumentException si la dirección base falta o no es una dirección web
     */
    public static CorreoDeVerificacion para(Usuario cuenta, String direccionBase, String valorToken) {
        Objects.requireNonNull(cuenta, "La cuenta es obligatoria.");
        Objects.requireNonNull(valorToken, "El valor del token es obligatorio.");
        if (direccionBase == null
                || !(direccionBase.startsWith("https://") || direccionBase.startsWith("http://"))) {
            throw new IllegalArgumentException(
                    "La dirección base de los enlaces debe empezar por http:// o https://.");
        }
        String base = direccionBase.endsWith("/")
                ? direccionBase.substring(0, direccionBase.length() - 1)
                : direccionBase;
        return new CorreoDeVerificacion(cuenta.correoOriginal(), cuenta.nombre(),
                base + RUTA_PAGINA + "#token=" + valorToken);
    }

    /**
     * Devuelve un texto para depuración sin el destinatario ni el enlace.
     *
     * @return un texto fijo
     */
    @Override
    public String toString() {
        return "CorreoDeVerificacion[datos ocultos]";
    }
}