package com.smartkash.identidad.registro.puertos;

import com.smartkash.identidad.registro.dominio.Autorizacion;

/**
 * Lugar donde se guardan las pruebas de autorización sobre el tratamiento de datos.
 *
 * <p>Qué es: un puerto. Solo ofrece guardar, porque las autorizaciones son un registro
 * de solo inserción: nunca se modifican ni se borran una a una.</p>
 */
public interface RepositorioAutorizaciones {

    /**
     * Guarda una prueba de autorización.
     *
     * <p>Reglas: la cuenta a la que pertenece debe haberse guardado antes, en la misma
     * transacción, para que nunca quede una cuenta sin su prueba ni una prueba sin cuenta.</p>
     *
     * @param autorizacion la prueba a guardar
     */
    void guardar(Autorizacion autorizacion);
}
