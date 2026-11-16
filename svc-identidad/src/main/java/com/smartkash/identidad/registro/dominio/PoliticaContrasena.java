package com.smartkash.identidad.registro.dominio;

import com.smartkash.identidad.registro.dominio.ErrorDeRegistro.Motivo;
import com.smartkash.identidad.registro.puertos.ContrasenasComunes;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Reglas que debe cumplir una contraseña para poder registrarse.
 *
 * <p>Qué hace: decide si una contraseña es aceptable. Se acepta solo si tiene entre
 * {@value #LARGO_MINIMO} y {@value #LARGO_MAXIMO} caracteres, no ocupa más de
 * {@value #BYTES_MAXIMOS} bytes en UTF-8, no es igual al correo de la persona y no está
 * en la lista de contraseñas comunes.</p>
 *
 * <p>De dónde vienen los datos: la contraseña llega del cuerpo de la solicitud de
 * registro, tal como la escribió la persona; el correo llega ya normalizado. A dónde
 * van: a ningún sitio. Esta clase solo responde "sirve" o "no sirve"; no guarda la
 * contraseña ni calcula su hash.</p>
 *
 * <p>Qué no hace: no recorta espacios ni transforma la contraseña. Los espacios cuentan
 * como cualquier otro carácter. Para comparar sin distinguir mayúsculas usa una copia en
 * minúsculas; la contraseña original no cambia.</p>
 *
 * <p>Reglas y errores: si la contraseña incumple varias reglas, informa solo la primera,
 * siempre en este orden: obligatoria, largo mínimo, largo máximo, bytes, igual al correo
 * y contraseña común. El error es un {@link ErrorDeRegistro} cuyo mensaje nunca incluye
 * la contraseña ni el correo.</p>
 */
public class PoliticaContrasena {

    /** Cantidad mínima de caracteres. */
    public static final int LARGO_MINIMO = 12;

    /** Cantidad máxima de caracteres. */
    public static final int LARGO_MAXIMO = 64;

    /** Tamaño máximo de la contraseña codificada en UTF-8. */
    public static final int BYTES_MAXIMOS = 72;

    /** Nombre del campo en el cuerpo de la solicitud; viaja en el error. */
    private static final String CAMPO = "contrasena";

    private final ContrasenasComunes contrasenasComunes;

    /**
     * Crea la política con la lista de contraseñas comunes que debe consultar.
     *
     * @param contrasenasComunes la lista donde se busca si una contraseña es demasiado
     *                           conocida; obligatoria
     * @throws NullPointerException si la lista es nula
     */
    public PoliticaContrasena(ContrasenasComunes contrasenasComunes) {
        this.contrasenasComunes = Objects.requireNonNull(
                contrasenasComunes, "La lista de contraseñas comunes es obligatoria.");
    }

    /**
     * Comprueba que la contraseña cumple todas las reglas. Si las cumple, termina sin
     * devolver nada; si no, lanza un error con el motivo.
     *
     * @param contrasena la contraseña tal como la escribió la persona, sin recortar
     * @param correo     el correo de la persona, ya normalizado; obligatorio
     * @throws ErrorDeRegistro      si la contraseña incumple alguna regla; indica el
     *                              motivo y el campo {@code contrasena}
     * @throws NullPointerException si el correo es nulo, porque eso es un fallo de quien
     *                              llama a este método y no de la persona que se registra
     */
    public void validar(String contrasena, CorreoNormalizado correo) {
        Objects.requireNonNull(correo, "El correo es obligatorio para validar la contraseña.");

        if (contrasena == null || contrasena.isEmpty()) {
            throw error(Motivo.CONTRASENA_OBLIGATORIA, "La contraseña es obligatoria.");
        }

        // String.length() cuenta unidades internas de Java, y un emoji ocupa dos. Los
        // "puntos de código" son los caracteres tal como los entiende la persona: un
        // emoji cuenta como uno.
        int caracteres = contrasena.codePointCount(0, contrasena.length());
        if (caracteres < LARGO_MINIMO) {
            throw error(Motivo.CONTRASENA_MUY_CORTA,
                    "La contraseña debe tener al menos " + LARGO_MINIMO + " caracteres.");
        }
        if (caracteres > LARGO_MAXIMO) {
            throw error(Motivo.CONTRASENA_MUY_LARGA,
                    "La contraseña no puede tener más de " + LARGO_MAXIMO + " caracteres.");
        }

        // Una letra con tilde ocupa 2 bytes y un emoji 4, así que una contraseña puede
        // caber en el máximo de caracteres y aun así superar el máximo de bytes.
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS) {
            throw error(Motivo.CONTRASENA_SUPERA_BYTES,
                    "La contraseña es demasiado larga: usa menos caracteres especiales o emojis.");
        }

        // Copia en minúsculas solo para comparar. Locale.ROOT hace que la conversión sea
        // igual en cualquier máquina, igual que al normalizar el correo.
        String enMinusculas = contrasena.toLowerCase(Locale.ROOT);

        if (enMinusculas.equals(correo.valor())) {
            throw error(Motivo.CONTRASENA_IGUAL_AL_CORREO,
                    "La contraseña no puede ser igual al correo.");
        }

        if (contrasenasComunes.contiene(enMinusculas)) {
            throw error(Motivo.CONTRASENA_COMUN,
                    "La contraseña es demasiado común. Elige otra.");
        }
    }

    /** Arma el error de este campo; el mensaje nunca debe incluir la contraseña. */
    private static ErrorDeRegistro error(Motivo motivo, String mensaje) {
        return new ErrorDeRegistro(motivo, CAMPO, mensaje);
    }
}
