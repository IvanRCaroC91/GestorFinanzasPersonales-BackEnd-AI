package com.smartkash.finanzas.cuentas.puertos;
import com.smartkash.finanzas.cuentas.dominio.Cuenta;

import java.util.List;
import java.util.UUID;

/**
 * Lo que el caso de uso necesita de la base de datos, dicho en términos del negocio.
 *
 * <p>Qué es un puerto: una interfaz que describe una necesidad sin decir cómo se cumple.
 * El caso de uso depende de esta interfaz, no de PostgreSQL ni de JPA; el adaptador que la implementa vive aparte.
 * Así el caso de uso se prueba con un repositorio falso, sin base de datos, y cambiar de tecnología de almacenamiento no obliga a tocarlo.</p>
 */

public interface RepositorioCuentas {

    /**
     * Busca todas las cuentas de una persona, incluidas las archivadas, ordenadas por nombre.
     *
     * @param usuarioId identificador del dueño de las cuentas
     * @return sus cuentas, o una lista vacía si no tiene ninguna; nunca incluye cuentas de otra persona
     */

    List<Cuenta> buscarCuentasUsuario (UUID usuarioId);
}
