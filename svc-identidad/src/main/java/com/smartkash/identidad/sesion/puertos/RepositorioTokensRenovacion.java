package com.smartkash.identidad.sesion.puertos;

import com.smartkash.identidad.sesion.dominio.TokenRenovacion;

/**
 * Lugar donde se guardan los tokens de renovación emitidos.
 *
 * <p>Qué es: un puerto. El caso de uso de inicio de sesión solo sabe que puede pedir que se
 * guarde un token; no sabe que detrás hay una tabla. Por ahora solo se guarda: consultarlos
 * y anularlos llegará con la renovación y el cierre de sesión.</p>
 */
@FunctionalInterface
public interface RepositorioTokensRenovacion {

    /**
     * Guarda un token de renovación recién emitido.
     *
     * <p>Lo que se guarda es la huella del valor, que es lo único que lleva
     * {@link TokenRenovacion}; el valor en claro nunca llega aquí.</p>
     *
     * @param token el token a guardar
     */
    void guardar(TokenRenovacion token);
}