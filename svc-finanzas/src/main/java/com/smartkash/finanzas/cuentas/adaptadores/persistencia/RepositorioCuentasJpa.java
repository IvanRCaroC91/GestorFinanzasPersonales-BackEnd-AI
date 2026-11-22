package com.smartkash.finanzas.cuentas.adaptadores.persistencia;

import com.smartkash.finanzas.cuentas.dominio.Cuenta;
import com.smartkash.finanzas.cuentas.dominio.TipoDeCuenta;
import com.smartkash.finanzas.cuentas.puertos.RepositorioCuentas;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Implementa el puerto de cuentas usando JPA.
 *
 * <p>Qué hace: pide las filas a {@link CuentasSpringData} y las convierte en objetos de negocio.
 * Es el único sitio que conoce las dos formas de una cuenta: la de la tabla y la del dominio.</p>
 *
 * <p>Regla: solo devuelve las filas del dueño indicado; la condición va en la propia consulta,
 * no se filtra después en memoria.</p>
 */

@Repository
class RepositorioCuentasJpa implements RepositorioCuentas {

    private final CuentasSpringData cuentas;

    /**
     * Crea el adaptador con las consultas sobre la tabla.
     *
     * @param cuentas las consultas sobre la tabla; la entrega Spring
     */
    RepositorioCuentasJpa(CuentasSpringData cuentas) {
        this.cuentas = cuentas;
    }

    @Override
    public List<Cuenta> buscarCuentasUsuario(UUID usuarioId){
        return cuentas.findByUsuarioRefOrderByNombreAsc(usuarioId).stream()
                .map(RepositorioCuentasJpa::aDominio)
                .toList();
    }

    /** Convierte una fila de la tabla en la cuenta del negocio. */
    private static Cuenta aDominio(CuentaEntidad fila){
        return new Cuenta(
                fila.id(),
                TipoDeCuenta.valueOf(fila.tipo()),
                fila.nombre(),
                fila.saldoInicial(),
                fila.fechaInicio(),
                fila.predeterminada(),
                fila.archivada());
    }

}
