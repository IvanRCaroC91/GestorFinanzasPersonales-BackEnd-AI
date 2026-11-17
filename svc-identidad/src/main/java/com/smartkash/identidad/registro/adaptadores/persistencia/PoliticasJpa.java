package com.smartkash.identidad.registro.adaptadores.persistencia;

import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Consultas sobre la tabla de versiones de la política.
 *
 * <p>Qué es: una interfaz de Spring Data; Spring crea su código al arrancar. La usa
 * {@link RepositorioPoliticasPostgres}.</p>
 */
interface PoliticasJpa extends Repository<PoliticaEntidad, String> {

    /**
     * Devuelve la versión con la fecha de publicación más reciente. Spring deduce la
     * consulta del nombre: "busca la primera, ordenando por fecha de publicación de mayor a
     * menor".
     *
     * @return la última versión publicada, o vacío si la tabla no tiene ninguna
     */
    Optional<PoliticaEntidad> findFirstByOrderByPublicadaEnDesc();
}