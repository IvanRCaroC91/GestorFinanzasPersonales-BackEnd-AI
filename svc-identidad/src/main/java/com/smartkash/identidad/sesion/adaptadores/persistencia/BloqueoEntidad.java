package com.smartkash.identidad.sesion.adaptadores.persistencia;

import com.smartkash.identidad.sesion.dominio.ControlDeIntentos;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Fila de la tabla de contadores de acceso vista desde el código.
 *
 * <p>Qué es: la entidad JPA de un contador de inicios de sesión fallidos. Cada fila es el
 * contador de una cuenta o de una dirección IP, identificado por una huella y nunca por el
 * correo o la dirección en claro.</p>
 *
 * <p>De dónde vienen los datos: las filas nuevas se insertan con la consulta de
 * {@link BloqueosJpa#insertarSiNoExiste}; las existentes se leen de la tabla y se modifican
 * con {@link #cambiarA}. A dónde van: de vuelta a la tabla. Quien la convierte en la clase de
 * negocio {@link ControlDeIntentos} es {@link #aDominio}.</p>
 *
 * <p>Cómo se guardan los cambios: no hace falta llamar a ningún método de guardado. JPA
 * recuerda cómo estaba la fila al leerla y, al confirmar la transacción, escribe en la tabla
 * lo que haya cambiado. Esto se llama comprobación de cambios.</p>
 *
 * <p>Regla: al arrancar, se comprueba que estas columnas existen en la tabla que crearon las
 * migraciones; si no, el servicio no arranca.</p>
 */
@Entity
@Table(name = "bloqueos_acceso")
class BloqueoEntidad {

    /*
     * La columna es char(64), de largo fijo. Sin @JdbcTypeCode, Hibernate supone varchar y la
     * comprobación del arranque falla porque los tipos no coinciden.
     */
    @Id
    @Column(name = "clave_hash", length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String claveHash;

    @Column(name = "tipo")
    private String tipo;

    /* short porque la columna es smallint: la comprobación del arranque exige el mismo tipo. */
    @Column(name = "fallos")
    private short fallos;

    @Column(name = "ventana_inicio")
    private Instant ventanaInicio;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    /** JPA exige un constructor sin parámetros para poder crear el objeto al leer una fila. */
    protected BloqueoEntidad() {
    }

    /**
     * Convierte la fila en la clase de negocio.
     *
     * @return el contador con sus fallos, el inicio de su ventana y su bloqueo, si lo hay
     */
    ControlDeIntentos aDominio() {
        return new ControlDeIntentos(fallos, ventanaInicio, bloqueadoHasta);
    }

    /**
     * Copia en la fila el nuevo estado del contador. Se escribirá en la tabla al confirmar la
     * transacción.
     *
     * @param contador el estado calculado por las reglas del dominio
     */
    void cambiarA(ControlDeIntentos contador) {
        this.fallos = (short) contador.fallos();
        this.ventanaInicio = contador.ventanaInicio();
        this.bloqueadoHasta = contador.bloqueadoHasta();
    }
}