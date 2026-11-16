package com.smartkash.identidad.registro.puertos;

import java.util.Optional;

/**
 * Lugar donde se consultan las versiones publicadas de la política de tratamiento de
 * datos personales.
 *
 * <p>Qué es: un puerto. El registro necesita saber cuál es la versión vigente para
 * comprobar que la persona aceptó esa y no una anterior.</p>
 */
public interface RepositorioPoliticas {

    /**
     * Devuelve la versión vigente de la política, es decir, la última publicada.
     *
     * @return la versión vigente (por ejemplo, {@code "1.0"}), o vacío si todavía no se
     *         ha publicado ninguna; en ese caso nadie puede registrarse
     */
    Optional<String> versionVigente();
}
