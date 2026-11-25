package com.smartkash.finanzas.cuentas.aplicacion;

import com.smartkash.finanzas.cuentas.dominio.Cuenta;
import com.smartkash.finanzas.cuentas.puertos.RepositorioCuentas;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Caso de uso: ver las cuentas de una persona.
 *
 * <p>Regla de seguridad: el dueño llega como parámetro y quien llama lo toma del token de acceso,
 * nunca de lo que escribió el cliente. Este caso de uso solo busca las cuentas de ese dueño,
 * así que no hay forma de pedir las de otra persona.</p>
 *
 * <p>Es Java puro: no usa Spring. Las clases de configuración lo crean desde fuera, para que se
 * pueda probar rápido y sin arrancar nada.</p>
 */

public class ConsultarCuentas {
    private final RepositorioCuentas cuentas;

    /**
     * Crea el caso de uso con el repositorio por el que se leen las cuentas.
     *
     * @param cuentas el puerto por el que se leen las cuentas
     * @throws NullPointerException si falta el repositorio
     */
    public ConsultarCuentas(RepositorioCuentas cuentas) {
        this.cuentas = Objects.requireNonNull(cuentas, "El repositorio de cuentas es obligatorio");
    }

    /**
     * Devuelve las cuentas de la persona.
     *
     * @param usuarioId identificador del dueño, tomado del token de acceso
     * @return sus cuentas, ordenadas por nombre; vacía si no tiene
     * @throws NullPointerException si falta el usuario
     */

    public List<Cuenta> consultarCuentasUsuario (UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "El usuario es obligatorio");
        return cuentas.buscarCuentasUsuario(usuarioId);
    }

}
