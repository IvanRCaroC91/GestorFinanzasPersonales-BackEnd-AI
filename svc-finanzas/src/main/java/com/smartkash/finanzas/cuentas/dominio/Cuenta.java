package com.smartkash.finanzas.cuentas.dominio;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuenta financiera de una persona: Lugar donde esta su dinero
 *<p>Qué es la clase {@code record}:
 * una clase para datos que no cambian. Java crea solo el constructor, los accesos y la comparación.
 * El bloque del constructor compacto valida los datos, así que no puede existir una cuenta con un nombre vacío.</p>
 *
 * <p>Reglas:
 * el nombre se guarda sin espacios al inicio ni al final y tiene entre 1 y {@value #LARGO_MAXIMO_DEL_NOMBRE} caracteres.
 * El saldo inicial puede ser negativo, porque una cuenta corriente puede estar en descubierto.
 *
 * El importe es un {@link BigDecimal}, nunca un {@code double}:
 * los decimales binarios no representan bien los céntimos.</p>
 *
 * <p>Esta clase no sabe nada de la base de datos ni de la web</p>
 *
 * @param id             identificador de la cuenta
 * @param tipo           Tipo de cuenta - Viene de la clase tipo ENUM TipoDeCuenta.java
 * @param nombre         nombre visible
 * @param saldoInicial   saldo en la fecha de inicio
 * @param fechaInicio    fecha desde la que se calcula el saldo
 * @param predeterminada si es la cuenta sugerida por defecto
 * @param archivada      si está oculta para registros nuevos
 *
 */


public record Cuenta(
        UUID id,
        TipoDeCuenta tipo,
        String nombre,
        BigDecimal saldoInicial,
        LocalDate fechaInicio,
        boolean predeterminada,
        boolean archivada){

    /** Mayor largo admitido para el nombre; coincide con la columna de la tabla. */
    public static final int LARGO_MAXIMO_DEL_NOMBRE = 60;

    /**
     * Validacion de los datos al crear la cuenta.
     *
     * @throws NullPointerException     si falta algún dato obligatorio
     * @throws IllegalArgumentException si el nombre queda vacío o supera el largo máximo
     */

    public Cuenta {
        Objects.requireNonNull(id, "El identificador es obligatorio");
        Objects.requireNonNull(tipo, "El tipo de cuenta es obligatorio");
        Objects.requireNonNull(nombre, "El nombre es obligatorio");
        Objects.requireNonNull(saldoInicial, "El saldo de inicial es obligatorio");
        Objects.requireNonNull(fechaInicio, "la fecha inicial es obligatoria");

        nombre = nombre.strip();
        if (nombre.isEmpty() || nombre.length() > LARGO_MAXIMO_DEL_NOMBRE) {
            throw new IllegalArgumentException("El nombre debe tener entre 1 y " + LARGO_MAXIMO_DEL_NOMBRE + " caracteres");
        }
    }
}
