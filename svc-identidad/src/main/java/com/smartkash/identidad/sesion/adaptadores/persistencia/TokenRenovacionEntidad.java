package com.smartkash.identidad.sesion.adaptadores.persistencia;

import com.smartkash.identidad.sesion.dominio.TokenRenovacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla de tokens de renovación vista desde el código.
 *
 * <p>Qué es: la entidad con la que se inserta cada token de renovación. De dónde vienen los
 * datos: de la clase de negocio {@link TokenRenovacion}, mediante {@link #desde}. A dónde
 * van: a la tabla de tokens de renovación. Solo se guarda la huella del token, nunca su
 * valor.</p>
 *
 * <p>Qué columnas declara: las que se llenan al emitir el token. Las de uso y revocación
 * quedan vacías y se declararán cuando lleguen la renovación y el cierre de sesión.</p>
 *
 * <p>Por qué implementa {@code Persistable}: el identificador lo genera el código, así que
 * Spring Data no puede saber por sí solo si la fila es nueva y, si no se le dice, primero la
 * buscaría. {@link #isNew()} responde siempre que sí, porque por ahora solo se insertan.</p>
 */
@Entity
@Table(name = "tokens_renovacion")
class TokenRenovacionEntidad implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "familia_id")
    private UUID familiaId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    /* La columna es char(64), de largo fijo; ver la misma nota en BloqueoEntidad. */
    @Column(name = "hash_token", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String hashToken;

    @Column(name = "emitido_en")
    private Instant emitidoEn;

    @Column(name = "expira_en")
    private Instant expiraEn;

    /** JPA exige un constructor sin parámetros. */
    protected TokenRenovacionEntidad() {
    }

    /**
     * Convierte el token del negocio en una fila lista para insertar.
     *
     * @param token el token recién emitido
     * @return la fila correspondiente
     */
    static TokenRenovacionEntidad desde(TokenRenovacion token) {
        TokenRenovacionEntidad fila = new TokenRenovacionEntidad();
        fila.id = token.id();
        fila.familiaId = token.familiaId();
        fila.usuarioId = token.usuarioId();
        fila.hashToken = token.huella();
        fila.emitidoEn = token.emitidoEn();
        fila.expiraEn = token.expiraEn();
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
     * @return siempre {@code true}: por ahora los tokens solo se insertan
     */
    @Override
    public boolean isNew() {
        return true;
    }
}