package com.smartkash.finanzas.cuentas.dominio;

/**
 * Clases de cuenta que puede tener una persona.
 *
 * <p>Qué es un {@code enum}: una lista cerrada de valores. Garantiza que en el código no pueda
 * existir un tipo de cuenta que no esté aquí. La lista coincide con la restricción de la tabla
 * de cuentas, que rechaza cualquier otro valor.</p>
 */

public enum TipoDeCuenta {

    /** Dinero físico que la persona lleva consigo. */
    EFECTIVO,

    /** Cuenta de ahorros de una entidad financiera. */
    AHORROS,

    /** Cuenta corriente, que puede tener saldo negativo. */
    CORRIENTE,

    /** Billetera digital. */
    BILLETERA

}
