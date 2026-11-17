package com.smartkash.identidad.verificacion.adaptadores.persistencia;

import com.smartkash.identidad.verificacion.puertos.UnidadDeTrabajo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ejecuta un bloque de trabajo dentro de una transacción de base de datos.
 *
 * <p>Qué es: el adaptador del puerto {@link UnidadDeTrabajo}. Usa {@code TransactionTemplate},
 * la herramienta de Spring para abrir una transacción desde código: la abre, ejecuta el bloque
 * y la confirma; si el bloque lanza una excepción, la deshace.</p>
 *
 * <p>Todas las operaciones del bloque usan la misma conexión, tanto las hechas con JPA como las
 * hechas con SQL directo, así que se confirman o se deshacen juntas.</p>
 */
@Component
class UnidadDeTrabajoConTransaccion implements UnidadDeTrabajo {

    private final TransactionTemplate plantilla;

    /**
     * @param gestor el gestor de transacciones del servicio; lo entrega Spring
     */
    UnidadDeTrabajoConTransaccion(PlatformTransactionManager gestor) {
        this.plantilla = new TransactionTemplate(gestor);
    }

    /**
     * Ejecuta el trabajo dentro de una transacción.
     *
     * @param trabajo lo que hay que hacer
     */
    @Override
    public void ejecutar(Runnable trabajo) {
        plantilla.executeWithoutResult(estado -> trabajo.run());
    }
}
