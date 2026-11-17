package com.smartkash.identidad.sesion.puertos;

import com.smartkash.identidad.sesion.dominio.ControlDeIntentos;
import com.smartkash.identidad.sesion.dominio.TipoDeContador;

import java.time.Instant;
import java.util.Optional;

/**
 * Lugar donde se guardan los contadores de inicios de sesión fallidos.
 *
 * <p>Qué es: un puerto. El caso de uso de inicio de sesión solo sabe que puede consultar un
 * contador, anotar un fallo y olvidar un contador; no sabe que detrás hay una tabla.</p>
 *
 * <p>Cómo se identifica cada contador: por la huella de un texto, nunca por el texto. Para
 * una cuenta es la huella de {@code CUENTA:<correo normalizado>} y para una dirección IP,
 * la de {@code IP:<dirección>}. Así ni el correo ni la dirección quedan escritos en claro.
 * Calcular esa huella es tarea de quien llama.</p>
 */
public interface RepositorioBloqueos {

    /**
     * Busca el contador de una cuenta o de una dirección IP.
     *
     * @param huellaClave huella que identifica al contador
     * @return el contador, o una caja vacía si no hay fallos anotados
     */
    Optional<ControlDeIntentos> buscar(String huellaClave);

    /**
     * Anota un inicio de sesión fallido y devuelve cómo queda el contador.
     *
     * <p>Reglas: si no había contador, lo crea con este primer fallo. Si lo había, aplica
     * las reglas de {@link ControlDeIntentos#trasOtroFallo}, con el límite que corresponde
     * al tipo. Leer, calcular y guardar deben ocurrir como una sola operación: si dos
     * fallos llegan a la vez, ninguno puede perderse.</p>
     *
     * @param huellaClave huella que identifica al contador
     * @param tipo        si el contador es de una cuenta o de una dirección IP
     * @param ahora       momento del fallo
     * @return el contador ya actualizado y guardado
     */
    ControlDeIntentos anotarFallo(String huellaClave, TipoDeContador tipo, Instant ahora);

    /**
     * Borra un contador. Se usa cuando la persona inicia sesión correctamente.
     *
     * <p>Si no existe, no hace nada.</p>
     *
     * @param huellaClave huella que identifica al contador
     */
    void olvidar(String huellaClave);
}