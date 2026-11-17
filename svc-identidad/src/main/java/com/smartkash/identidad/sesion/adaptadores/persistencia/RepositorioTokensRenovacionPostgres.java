package com.smartkash.identidad.sesion.adaptadores.persistencia;

import com.smartkash.identidad.sesion.dominio.TokenRenovacion;
import com.smartkash.identidad.sesion.puertos.RepositorioTokensRenovacion;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda los tokens de renovación en PostgreSQL.
 *
 * <p>Qué es: el adaptador que implementa el puerto {@link RepositorioTokensRenovacion}.
 * De dónde vienen los datos: del caso de uso de inicio de sesión, que ya calculó la huella.
 * A dónde van: a la tabla de tokens de renovación.</p>
 */
@Repository
class RepositorioTokensRenovacionPostgres implements RepositorioTokensRenovacion {

    private final TokensRenovacionJpa tabla;

    /**
     * @param tabla operaciones sobre la tabla de tokens de renovación; la entrega Spring
     */
    RepositorioTokensRenovacionPostgres(TokensRenovacionJpa tabla) {
        this.tabla = tabla;
    }

    /**
     * Guarda un token de renovación recién emitido.
     *
     * <p>Errores: si la cuenta dueña no existe o la huella ya estuviera guardada, la base de
     * datos rechaza la fila y Spring lo informa con una excepción de integridad de datos.
     * Ninguno de los dos casos debería ocurrir: la cuenta se acaba de leer y la huella sale
     * de un valor aleatorio de 256 bits.</p>
     *
     * @param token el token a guardar
     */
    @Override
    @Transactional
    public void guardar(TokenRenovacion token) {
        tabla.saveAndFlush(TokenRenovacionEntidad.desde(token));
    }
}