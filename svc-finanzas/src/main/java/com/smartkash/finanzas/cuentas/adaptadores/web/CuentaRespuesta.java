package com.smartkash.finanzas.cuentas.adaptadores.web;

import com.smartkash.finanzas.cuentas.dominio.Cuenta;
import com.smartkash.finanzas.cuentas.dominio.TipoDeCuenta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una cuenta tal como la ve quien consume la API: es el contenido del JSON de respuesta.
 *
 * <p>Por qué existe aparte de {@link Cuenta}: la forma del JSON es un compromiso con quien
 * usa la API y no debe cambiar por reorganizaciones internas del dominio. Esta clase fija ese
 * contrato; {@code Cuenta} puede evolucionar sin romperlo.</p>
 *
 * <p>Formato: el saldo es un número y la fecha se escribe como {@code AAAA-MM-DD}. No incluye
 * el dueño de la cuenta: quien la pide ya sabe que es suya.</p>
 *
 * @param id             identificador de la cuenta
 * @param tipo           clase de cuenta
 * @param nombre         nombre visible
 * @param saldoInicial   saldo en la fecha de inicio
 * @param fechaInicio    fecha desde la que se calcula el saldo
 * @param predeterminada si es la cuenta sugerida por defecto
 * @param archivada      si está oculta para registros nuevos
 */
record CuentaRespuesta(
        UUID id,
        TipoDeCuenta tipo,
        String nombre,
        BigDecimal saldoInicial,
        LocalDate fechaInicio,
        boolean predeterminada,
        boolean archivada) {

    /**
     * Convierte una cuenta del negocio en su representación para la API.
     *
     * @param cuenta la cuenta del dominio
     * @return la representación lista para convertirse en JSON
     */
    static CuentaRespuesta de(Cuenta cuenta) {
        return new CuentaRespuesta(
                cuenta.id(),
                cuenta.tipo(),
                cuenta.nombre(),
                cuenta.saldoInicial(),
                cuenta.fechaInicio(),
                cuenta.predeterminada(),
                cuenta.archivada());
    }
}