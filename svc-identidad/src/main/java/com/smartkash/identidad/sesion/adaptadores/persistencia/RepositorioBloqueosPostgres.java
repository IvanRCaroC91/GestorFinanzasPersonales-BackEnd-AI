package com.smartkash.identidad.sesion.adaptadores.persistencia;

import com.smartkash.identidad.sesion.dominio.ControlDeIntentos;
import com.smartkash.identidad.sesion.dominio.TipoDeContador;
import com.smartkash.identidad.sesion.puertos.RepositorioBloqueos;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Guarda y consulta los contadores de inicios de sesión fallidos en PostgreSQL.
 *
 * <p>Qué es: el adaptador que implementa el puerto {@link RepositorioBloqueos}. Traduce entre
 * la clase de negocio {@link ControlDeIntentos} y la tabla de contadores de acceso.</p>
 *
 * <p>De dónde vienen los datos: del caso de uso de inicio de sesión, que entrega la huella
 * del contador, el tipo y la hora. A dónde van: a la tabla de contadores de acceso.</p>
 *
 * <p>Cómo se evita perder fallos simultáneos: las reglas (cuántos fallos, cuánto bloqueo)
 * viven solo en el dominio, en Java. Para aplicarlas sin que dos peticiones se pisen, se lee
 * la fila bloqueándola, se calcula el nuevo estado con el dominio y se guarda, todo dentro de
 * una transacción. Mientras tanto, cualquier otra petición sobre el mismo contador espera su
 * turno. La alternativa, escribir las reglas en una sola sentencia SQL, obligaría a tener la
 * espera creciente y el reinicio de la ventana duplicados en dos lenguajes.</p>
 */
@Repository
class RepositorioBloqueosPostgres implements RepositorioBloqueos {

    private final BloqueosJpa tabla;

    /**
     * @param tabla consultas sobre la tabla de contadores de acceso; la entrega Spring
     */
    RepositorioBloqueosPostgres(BloqueosJpa tabla) {
        this.tabla = tabla;
    }

    /**
     * Busca el contador de una cuenta o de una dirección IP, sin bloquear nada.
     *
     * @param huellaClave huella que identifica al contador
     * @return el contador, o una caja vacía si no hay fallos anotados
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ControlDeIntentos> buscar(String huellaClave) {
        return tabla.findById(huellaClave).map(BloqueoEntidad::aDominio);
    }

    /**
     * Anota un fallo y devuelve cómo queda el contador.
     *
     * <p>Pasos, dentro de una sola transacción:</p>
     * <ol>
     *   <li>Buscar la fila bloqueándola. Si existe, aplicar
     *       {@link ControlDeIntentos#trasOtroFallo} y dejar que JPA escriba el cambio al
     *       confirmar.</li>
     *   <li>Si no existe, intentar crearla con el primer fallo. Si otra petición la creó un
     *       instante antes, la inserción no hace nada y se vuelve al paso 1, que ahora sí la
     *       encuentra y suma sobre ella.</li>
     * </ol>
     *
     * <p>{@code @Transactional}: Spring abre la transacción al entrar y la confirma al salir;
     * el bloqueo de la fila dura hasta ese momento.</p>
     *
     * @param huellaClave huella que identifica al contador
     * @param tipo        si el contador es de una cuenta o de una dirección IP
     * @param ahora       momento del fallo
     * @return el contador ya actualizado
     */
    @Override
    @Transactional
    public ControlDeIntentos anotarFallo(String huellaClave, TipoDeContador tipo, Instant ahora) {
        // El bucle da como mucho dos vueltas: si la inserción no hizo nada es porque la fila
        // ya existe, y la siguiente búsqueda la encuentra.
        while (true) {
            Optional<BloqueoEntidad> fila = tabla.buscarBloqueandoLaFila(huellaClave);
            if (fila.isPresent()) {
                ControlDeIntentos nuevo = fila.get().aDominio().trasOtroFallo(ahora, tipo.limite());
                fila.get().cambiarA(nuevo);
                return nuevo;
            }

            ControlDeIntentos primero = ControlDeIntentos.trasPrimerFallo(ahora, tipo.limite());
            int insertadas = tabla.insertarSiNoExiste(huellaClave, tipo.name(),
                    (short) primero.fallos(), primero.ventanaInicio(), primero.bloqueadoHasta());
            if (insertadas == 1) {
                return primero;
            }
        }
    }

    /**
     * Borra un contador. Si no existe, no hace nada.
     *
     * @param huellaClave huella que identifica al contador
     */
    @Override
    @Transactional
    public void olvidar(String huellaClave) {
        tabla.borrar(huellaClave);
    }
}