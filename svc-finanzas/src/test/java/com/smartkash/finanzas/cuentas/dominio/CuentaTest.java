package com.smartkash.finanzas.cuentas.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Pruebas de {@link Cuenta}.
 *
 * <p>Qué comprueban: que no puede existir una cuenta con datos inválidos. El constructor
 * compacto del {@code record} es la única puerta de entrada, así que basta con intentar crear
 * cuentas con datos buenos y malos y ver qué pasa.</p>
 *
 * <p>Son pruebas de dominio: no arrancan Spring ni tocan la base de datos, por eso tardan
 * milisegundos.</p>
 */
class CuentaTest {

    private static final UUID ID = UUID.randomUUID();
    private static final LocalDate FECHA = LocalDate.of(2026, 1, 1);

    /**
     * Los espacios que la persona deja sin querer al principio o al final no forman parte del
     * nombre. Si se guardaran, "Nómina" y "Nómina " parecerían dos cuentas distintas.
     */
    @Test
    void el_nombre_pierde_los_espacios_del_inicio_y_del_final() {
        Cuenta cuenta = cuentaConNombre("   Nómina  ");

        assertThat(cuenta.nombre()).isEqualTo("Nómina");
    }

    /**
     * Un nombre vacío o hecho solo de espacios, tabuladores o saltos de línea no sirve para
     * reconocer la cuenta. Se comprueba después de quitar los espacios, por eso "   " también
     * se rechaza.
     *
     * @param nombre nombre sin ningún carácter visible
     */
    @ParameterizedTest(name = "rechaza [{0}]")
    @ValueSource(strings = {"", "   ", "\t", " \n "})
    void rechaza_un_nombre_vacio_o_en_blanco(String nombre) {
        assertThatIllegalArgumentException().isThrownBy(() -> cuentaConNombre(nombre));
    }

    /** El límite está incluido: un nombre de exactamente 60 caracteres es válido. */
    @Test
    void acepta_un_nombre_de_60_caracteres() {
        String nombre = "a".repeat(Cuenta.LARGO_MAXIMO_DEL_NOMBRE);

        assertThat(cuentaConNombre(nombre).nombre()).hasSize(60);
    }

    /**
     * Un carácter más que el límite se rechaza. Coincide con la columna de la tabla, que admite
     * como mucho 60: así el error llega claro desde el dominio y no como un fallo de la base.
     */
    @Test
    void rechaza_un_nombre_de_61_caracteres() {
        String nombre = "a".repeat(Cuenta.LARGO_MAXIMO_DEL_NOMBRE + 1);

        assertThatIllegalArgumentException().isThrownBy(() -> cuentaConNombre(nombre));
    }

    /**
     * El largo se mide después de quitar los espacios de los extremos: 60 caracteres rodeados de
     * espacios siguen siendo un nombre válido de 60.
     */
    @Test
    void mide_el_largo_despues_de_quitar_los_espacios() {
        String nombre = "  " + "a".repeat(Cuenta.LARGO_MAXIMO_DEL_NOMBRE) + "  ";

        assertThat(cuentaConNombre(nombre).nombre()).hasSize(60);
    }

    /**
     * Una cuenta corriente puede empezar en descubierto, así que el saldo inicial negativo es
     * válido y se conserva tal cual, céntimos incluidos.
     */
    @Test
    void acepta_un_saldo_inicial_negativo() {
        BigDecimal saldo = new BigDecimal("-150.75");

        Cuenta cuenta = new Cuenta(ID, TipoDeCuenta.CORRIENTE, "Banco", saldo, FECHA, false, false);

        assertThat(cuenta.saldoInicial()).isEqualByComparingTo("-150.75");
    }

    /**
     * Cada dato obligatorio que falta se rechaza al crear la cuenta, y el mensaje dice cuál.
     * Los dos indicadores ({@code predeterminada} y {@code archivada}) son {@code boolean}
     * primitivos y no pueden ser nulos, así que no se prueban aquí.
     *
     * @param campo nombre del dato que se deja sin valor
     */
    @ParameterizedTest(name = "rechaza {0} nulo")
    @ValueSource(strings = {"id", "tipo", "nombre", "saldoInicial", "fechaInicio"})
    void rechaza_un_valor_nulo_en_cada_campo_obligatorio(String campo) {
        assertThatNullPointerException()
                .isThrownBy(() -> new Cuenta(
                        campo.equals("id") ? null : ID,
                        campo.equals("tipo") ? null : TipoDeCuenta.AHORROS,
                        campo.equals("nombre") ? null : "Ahorros",
                        campo.equals("saldoInicial") ? null : BigDecimal.ZERO,
                        campo.equals("fechaInicio") ? null : FECHA,
                        false,
                        false))
                .withMessageContaining("obligatori");
    }

    private static Cuenta cuentaConNombre(String nombre) {
        return new Cuenta(ID, TipoDeCuenta.AHORROS, nombre, BigDecimal.ZERO, FECHA, false, false);
    }
}
