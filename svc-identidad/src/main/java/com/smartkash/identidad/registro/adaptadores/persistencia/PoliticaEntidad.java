package com.smartkash.identidad.registro.adaptadores.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Fila de la tabla de versiones de la política de tratamiento de datos.
 *
 * <p>Qué es: una entidad de solo lectura. Las versiones se publican con migraciones, nunca
 * desde el código, así que esta clase no ofrece forma de crearlas ni de cambiarlas. Declara
 * solo las dos columnas que hacen falta para saber cuál es la versión vigente.</p>
 */
@Entity
@Table(name = "politicas_tratamiento")
class PoliticaEntidad {

    @Id
    private String version;

    @Column(name = "publicada_en")
    private Instant publicadaEn;

    /** JPA exige un constructor sin parámetros. */
    protected PoliticaEntidad() {
    }

    /**
     * @return el número de versión, por ejemplo {@code "1.0"}
     */
    String version() {
        return version;
    }
}