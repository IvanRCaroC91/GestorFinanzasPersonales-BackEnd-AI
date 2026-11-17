package com.smartkash.identidad.registro.adaptadores.persistencia;

import com.smartkash.identidad.registro.dominio.Autorizacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla de autorizaciones vista desde el código.
 *
 * <p>Qué es: la entidad con la que se inserta cada prueba de autorización. De dónde vienen
 * los datos: de la clase de negocio {@link Autorizacion}, mediante {@link #desde}. A dónde
 * van: a la tabla de autorizaciones.</p>
 *
 * <p>Por qué implementa {@code Persistable}: el identificador lo genera el código, no la
 * base de datos, y por eso Spring Data no puede saber por sí solo si la fila es nueva. Si no
 * se le dice, primero la busca y luego la inserta. Como esta tabla es de solo inserción,
 * {@link #isNew()} responde siempre que sí y se inserta directamente.</p>
 */
@Entity
@Table(name = "autorizaciones")
class AutorizacionEntidad implements Persistable<UUID> {

    /** Tipo de autorización que se registra al aceptar la política en el registro. */
    static final String TIPO_POLITICA_TRATAMIENTO = "POLITICA_TRATAMIENTO";

    @Id
    private UUID id;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    private String tipo;

    @Column(name = "politica_version")
    private String politicaVersion;

    private boolean otorgada;

    private String canal;

    @Column(name = "registrada_en")
    private Instant registradaEn;

    /** JPA exige un constructor sin parámetros. */
    protected AutorizacionEntidad() {
    }

    /**
     * Convierte la prueba de autorización del negocio en una fila lista para insertar.
     *
     * @param autorizacion la aceptación de la política que se va a guardar
     * @return la fila correspondiente, con el tipo de autorización de política
     */
    static AutorizacionEntidad desde(Autorizacion autorizacion) {
        AutorizacionEntidad fila = new AutorizacionEntidad();
        fila.id = autorizacion.id();
        fila.usuarioId = autorizacion.usuarioId();
        fila.tipo = TIPO_POLITICA_TRATAMIENTO;
        fila.politicaVersion = autorizacion.versionPolitica();
        fila.otorgada = autorizacion.otorgada();
        fila.canal = autorizacion.canal();
        fila.registradaEn = autorizacion.registradaEn();
        return fila;
    }

    /**
     * @return el identificador de la fila
     */
    @Override
    public UUID getId() {
        return id;
    }

    /**
     * @return siempre {@code true}: las autorizaciones solo se insertan, nunca se modifican
     */
    @Override
    public boolean isNew() {
        return true;
    }
}