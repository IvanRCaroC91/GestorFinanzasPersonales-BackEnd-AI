package com.smartkash.identidad.registro.adaptadores.seguridad;

import com.smartkash.identidad.registro.puertos.ContrasenasComunes;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lista de contraseñas comunes leída de un archivo de texto que acompaña al servicio.
 *
 * <p>Qué es: el adaptador que implementa el puerto {@link ContrasenasComunes}. Al crearse
 * lee el archivo una sola vez y deja las entradas en memoria; después cada consulta es
 * inmediata y no toca el disco.</p>
 *
 * <p>De dónde vienen los datos: de {@code seguridad/contrasenas-comunes.txt}, con una
 * contraseña por línea. Ese archivo sale de una lista pública de las contraseñas más usadas
 * (las que aparecen en filtraciones reales), de la que se conservaron solo las de 12 a 64
 * caracteres: las más cortas o más largas ya las rechazan las reglas de largo y nunca
 * llegarían a consultarse. A dónde van: la respuesta la usa la política de contraseñas para
 * aceptar o rechazar; aquí no se guarda ni se escribe en logs la contraseña consultada.</p>
 *
 * <p>Reglas: las entradas se guardan en minúsculas, porque quien pregunta entrega la
 * contraseña en minúsculas. Los espacios de cada línea se respetan, porque forman parte de
 * la contraseña. Las líneas vacías se ignoran.</p>
 *
 * <p>Errores: si el archivo no existe o no se puede leer, lanza
 * {@link IllegalStateException} al crearse y el servicio no arranca. Es preferible a seguir
 * con una lista vacía, que aceptaría cualquier contraseña común sin avisar.</p>
 */
@Component
class ContrasenasComunesDeArchivo implements ContrasenasComunes {

    /** Ubicación del archivo real dentro del servicio. */
    static final String ARCHIVO = "/seguridad/contrasenas-comunes.txt";

    private final Set<String> entradas;

    /** Crea la lista con el archivo real. Es el constructor que usa Spring. */
    ContrasenasComunesDeArchivo() {
        this(ARCHIVO);
    }

    /**
     * Crea la lista a partir de un archivo concreto; sirve para probar con uno pequeño.
     *
     * @param ruta ubicación del archivo dentro del servicio, empezando por {@code /}
     * @throws IllegalStateException si el archivo no existe o no se puede leer
     */
    ContrasenasComunesDeArchivo(String ruta) {
        this.entradas = leer(ruta);
    }

    /**
     * Indica si la contraseña está en la lista.
     *
     * @param contrasenaEnMinusculas la contraseña a consultar, ya en minúsculas
     * @return {@code true} si está en la lista y por tanto debe rechazarse
     */
    @Override
    public boolean contiene(String contrasenaEnMinusculas) {
        return entradas.contains(contrasenaEnMinusculas);
    }

    /**
     * @return cuántas contraseñas distintas tiene la lista
     */
    int cantidad() {
        return entradas.size();
    }

    /*
     * Se usa un conjunto (Set) y no una lista porque responde "¿está o no está?" sin
     * recorrer todas las entradas, y porque descarta los repetidos por sí solo.
     */
    private static Set<String> leer(String ruta) {
        InputStream archivo = ContrasenasComunesDeArchivo.class.getResourceAsStream(ruta);
        if (archivo == null) {
            throw new IllegalStateException("No se encontró la lista de contraseñas comunes: " + ruta);
        }

        Set<String> leidas = new HashSet<>();
        try (BufferedReader lector =
                     new BufferedReader(new InputStreamReader(archivo, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (!linea.isEmpty()) {
                    // Misma conversión que usa la política de contraseñas antes de preguntar.
                    leidas.add(linea.toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException | UncheckedIOException fallo) {
            throw new IllegalStateException(
                    "No se pudo leer la lista de contraseñas comunes: " + ruta, fallo);
        }
        return Set.copyOf(leidas);
    }
}