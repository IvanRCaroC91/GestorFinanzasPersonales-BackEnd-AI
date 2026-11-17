package com.smartkash.identidad.cuenta.dominio;

import java.util.Locale;

/**
 * Correo electrónico de una persona, guardado siempre en una única forma.
 *
 * <p>Qué hace: recibe el correo tal como lo escribió la persona y lo deja sin espacios
 * en los extremos y en minúsculas. Así {@code "  Ana@Correo.COM "} y
 * {@code "ana@correo.com"} se consideran el mismo correo, y una persona no puede crear
 * dos cuentas cambiando solo las mayúsculas.</p>
 *
 * <p>De dónde vienen los datos: del cuerpo de la solicitud de registro (y, más adelante,
 * del inicio de sesión). A dónde van: el valor normalizado es el que se busca y se guarda
 * en la base de datos, que además impide duplicados con un índice único.</p>
 *
 * <p>Qué no hace: no valida el formato del correo (que tenga arroba, dominio, etc.). Eso
 * lo comprueba la capa web antes de llegar aquí. Tampoco quita los espacios intermedios.</p>
 *
 * <p>Reglas y errores: lanza {@link IllegalArgumentException} si el correo es nulo, si
 * queda vacío después de quitar los espacios, o si supera los {@value #LARGO_MAXIMO}
 * caracteres, que es el largo máximo práctico de un correo según los estándares de
 * Internet. El mensaje de error no incluye el correo, para que no termine escrito en
 * los logs.</p>
 *
 * @param valor el correo ya normalizado: sin espacios en los extremos y en minúsculas
 */
public record CorreoNormalizado(String valor) {

    /** Largo máximo permitido, contado sobre el correo ya normalizado. */
    public static final int LARGO_MAXIMO = 254;

    /**
     * Normaliza y valida el correo al crear el objeto.
     *
     * <p>Es un constructor compacto: recibe {@code valor} tal como llega, lo transforma y,
     * al terminar, Java guarda el valor transformado en el campo.</p>
     *
     * @throws IllegalArgumentException si el correo es nulo, queda vacío o es demasiado largo
     */
    public CorreoNormalizado {
        if (valor == null) {
            throw new IllegalArgumentException("El correo es obligatorio.");
        }

        valor = valor.strip();
        if (valor.isEmpty()) {
            throw new IllegalArgumentException("El correo es obligatorio.");
        }

        // Locale.ROOT hace que el paso a minúsculas sea igual en cualquier máquina. Con el
        // idioma del sistema (por ejemplo, turco) la "I" se convertiría en una "ı" sin
        // punto y el mismo correo podría quedar guardado de dos formas distintas.
        valor = valor.toLowerCase(Locale.ROOT);

        // El largo se mide al final: algunos caracteres ocupan más al pasar a minúsculas,
        // y lo que importa es que el valor guardado no supere el máximo.
        if (valor.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException(
                    "El correo no puede tener más de " + LARGO_MAXIMO + " caracteres.");
        }
    }
}