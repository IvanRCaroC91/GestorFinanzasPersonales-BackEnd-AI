package com.smartkash.finanzas.cuentas.adaptadores.persistencia;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Consultas sobre la tabla de cuentas.
 *
 * <p>Qué es: una interfaz de Spring Data. No tiene clase que la implemente: Spring crea el
 * código al arrancar, a partir del nombre de cada metodo. Solo la usa
 * {@link RepositorioCuentasJpa}.</p>
 */

interface CuentasSpringData extends Repository<CuentaEntidad, UUID> {

    /**
     * Busca las filas de un dueño ordenadas por nombre. Spring deduce la consulta del nombre del
     * metodo: "encuentra por usuarioRef, ordenado por nombre ascendente".
     *
     * @param usuarioRef identificador del dueño
     * @return sus filas; vacía si no tiene
     */

    List<CuentaEntidad> findByUsuarioRefOrderByNombreAsc (UUID usuarioRef);



}
