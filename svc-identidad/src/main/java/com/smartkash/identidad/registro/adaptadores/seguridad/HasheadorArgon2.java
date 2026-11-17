package com.smartkash.identidad.registro.adaptadores.seguridad;

import com.smartkash.identidad.registro.puertos.HasheadorContrasena;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Calcula el hash de las contraseñas con el algoritmo Argon2id.
 *
 * <p>Qué es: el adaptador que implementa el puerto {@link HasheadorContrasena}. No programa
 * el algoritmo: delega en {@code Argon2PasswordEncoder}, la herramienta estándar de Spring
 * Security, para no escribir a mano código criptográfico.</p>
 *
 * <p>Por qué Argon2id: es un algoritmo pensado para contraseñas. Es lento y consume memoria
 * a propósito, de modo que a quien robe la base de datos le resulte muy caro probar millones
 * de contraseñas hasta acertar.</p>
 *
 * <p>De dónde vienen los datos: la contraseña llega del caso de uso del registro, ya
 * validada. A dónde van: el hash resultante se guarda en la tabla de usuarios. El hash lleva
 * escritos sus propios parámetros y su sal (un dato al azar distinto en cada cálculo), así
 * que para verificarlo después no hace falta guardar nada más.</p>
 *
 * <p>Reglas: la contraseña se usa tal como llega, sin recortarla, y no se escribe en logs ni
 * se conserva. Los parámetros son los mínimos que recomienda OWASP (organización de
 * referencia en seguridad de aplicaciones). Si algún día se suben, los hashes ya guardados
 * siguen siendo verificables porque cada uno recuerda con qué parámetros se calculó.</p>
 */
@Component
class HasheadorArgon2 implements HasheadorContrasena {

    /** Tamaño de la sal, en bytes. */
    private static final int BYTES_SAL = 16;

    /** Tamaño del hash resultante, en bytes. */
    private static final int BYTES_HASH = 32;

    /** Hilos que usa cada cálculo. */
    private static final int PARALELISMO = 1;

    /** Memoria que consume cada cálculo, en KiB: 19 MiB. */
    private static final int MEMORIA_KIB = 19_456;

    /** Veces que el algoritmo recorre esa memoria. */
    private static final int ITERACIONES = 2;

    private final Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
            BYTES_SAL, BYTES_HASH, PARALELISMO, MEMORIA_KIB, ITERACIONES);

    /**
     * Calcula el hash de una contraseña.
     *
     * @param contrasena la contraseña en claro, ya validada
     * @return el hash con sus parámetros y su sal incluidos; distinto en cada llamada aunque
     *         la contraseña sea la misma
     */
    @Override
    public String hashear(String contrasena) {
        return argon2.encode(contrasena);
    }
}