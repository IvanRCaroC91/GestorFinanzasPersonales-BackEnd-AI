package com.smartkash.identidad.cuenta.puertos;

/**
 * El servicio está haciendo ya todos los cálculos de contraseñas que admite a la vez y no se
 * liberó ningún turno a tiempo.
 *
 * <p>Qué significa: no es un fallo de la persona ni de sus datos, sino que el servidor está
 * ocupado. Quien la recibe puede reintentar al cabo de un momento. No lleva ningún dato: ni la
 * contraseña ni el correo.</p>
 *
 * <p>Quién la lanza: cualquier implementación de {@link HasheadorContrasena} que limite los
 * cálculos simultáneos. Quién la atiende: el manejador de errores web del servicio, que la
 * convierte en un {@code 429}. Los casos de uso no la capturan: la operación se corta entera y
 * no se guarda nada, igual que con cualquier otro error.</p>
 */
public class HasheadorSaturado extends RuntimeException {

    /** Crea el error, siempre con el mismo mensaje. */
    public HasheadorSaturado() {
        super("No hay turno libre para calcular el hash de una contraseña.");
    }
}
