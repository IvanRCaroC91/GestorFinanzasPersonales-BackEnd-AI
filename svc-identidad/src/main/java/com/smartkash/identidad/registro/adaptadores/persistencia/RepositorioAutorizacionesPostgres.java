package com.smartkash.identidad.registro.adaptadores.persistencia;

import com.smartkash.identidad.registro.dominio.Autorizacion;
import com.smartkash.identidad.registro.puertos.RepositorioAutorizaciones;
import org.springframework.stereotype.Repository;

/**
 * Guarda en PostgreSQL las pruebas de autorización sobre el tratamiento de datos.
 *
 * <p>Qué es: el adaptador que implementa el puerto {@link RepositorioAutorizaciones}. De
 * dónde vienen los datos: del caso de uso del registro. A dónde van: a la tabla de
 * autorizaciones, que solo admite inserciones.</p>
 */
@Repository
class RepositorioAutorizacionesPostgres implements RepositorioAutorizaciones {

    private final AutorizacionesJpa tabla;

    /**
     * @param tabla operaciones sobre la tabla de autorizaciones; la entrega Spring
     */
    RepositorioAutorizacionesPostgres(AutorizacionesJpa tabla) {
        this.tabla = tabla;
    }

    /**
     * Guarda una prueba de autorización.
     *
     * <p>Regla: la cuenta debe existir ya. Si no existe, o la versión de la política no
     * está publicada, la base de datos rechaza la fila y Spring lanza
     * {@code DataIntegrityViolationException}.</p>
     *
     * @param autorizacion la prueba a guardar
     */
    @Override
    public void guardar(Autorizacion autorizacion) {
        tabla.saveAndFlush(AutorizacionEntidad.desde(autorizacion));
    }
}