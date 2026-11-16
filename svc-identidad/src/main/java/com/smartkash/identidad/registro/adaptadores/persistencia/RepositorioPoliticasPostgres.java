package com.smartkash.identidad.registro.adaptadores.persistencia;

import com.smartkash.identidad.registro.puertos.RepositorioPoliticas;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Consulta en PostgreSQL las versiones publicadas de la política de tratamiento de datos.
 *
 * <p>Qué es: el adaptador que implementa el puerto {@link RepositorioPoliticas}. De dónde
 * vienen los datos: de la tabla de versiones de la política, que se llena con migraciones. A
 * dónde van: al caso de uso del registro, que compara la versión vigente con la que aceptó
 * la persona.</p>
 */
@Repository
class RepositorioPoliticasPostgres implements RepositorioPoliticas {

    private final PoliticasJpa tabla;

    /**
     * @param tabla consultas sobre la tabla de versiones; la entrega Spring
     */
    RepositorioPoliticasPostgres(PoliticasJpa tabla) {
        this.tabla = tabla;
    }

    /**
     * Devuelve la versión vigente de la política, es decir, la última publicada.
     *
     * @return la versión vigente, o vacío si todavía no se ha publicado ninguna
     */
    @Override
    public Optional<String> versionVigente() {
        return tabla.findFirstByOrderByPublicadaEnDesc().map(PoliticaEntidad::version);
    }
}