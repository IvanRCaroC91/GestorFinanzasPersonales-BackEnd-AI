package com.smartkash.finanzas.cuentas.adaptadores.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila de la tabla de cuentas vista desde el código.
 *
 * <p>Qué es: una entidad, una clase que JPA sabe relacionar con una tabla.
 * Cada objeto es una fila y cada campo una columna. No es la clase de negocio {@code Cuenta}: aquella contiene las
 * reglas y no sabe nada de la base de datos; esta solo describe cómo está guardada.</p>
 *
 * <p>Solo lectura por ahora: no tiene métodos que cambien valores, así que una fila leída no puede modificarse por accidente.
 * El tipo se guarda como texto, tal como está en la tabla; quien lo convierte al tipo del negocio es el adaptador del repositorio.</p>
 *
 * <p>Regla: al arrancar, se comprueba que estas columnas existen en la tabla que creó la
 * migración; si no existen, el servicio no arranca.</p>
 */

@Entity
@Table (name = "cuentas")
class CuentaEntidad {
    @Id
    private UUID id;

    @Column(name = "usuario_ref")
    private UUID usuarioRef;

    @Column(name = "tipo")
    private String tipo;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "saldo_inicial")
    private BigDecimal saldoInicial;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "predeterminada")
    private boolean predeterminada;

    @Column(name = "archivada")
    private boolean archivada;

    /** JPA exige un constructor sin parámetros para poder crear el objeto al leer una fila. */
    protected CuentaEntidad() {
    }

    /** @return identificador de la cuenta */
    UUID id() {
        return id;
    }

    /** @return dueño de la cuenta */
    UUID usuarioRef() {
        return usuarioRef;
    }

    /** @return tipo, como texto */
    String tipo() {
        return tipo;
    }

    /** @return nombre visible */
    String nombre() {
        return nombre;
    }

    /** @return saldo en la fecha de inicio */
    BigDecimal saldoInicial() {
        return saldoInicial;
    }

    /** @return fecha desde la que se calcula el saldo */
    LocalDate fechaInicio() {
        return fechaInicio;
    }

    /** @return si es la cuenta predeterminada */
    boolean predeterminada() {
        return predeterminada;
    }

    /** @return si está archivada */
    boolean archivada() {
        return archivada;
    }
}


