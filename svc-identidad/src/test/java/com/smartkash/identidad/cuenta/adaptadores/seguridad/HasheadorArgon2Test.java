package com.smartkash.identidad.cuenta.adaptadores.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el cálculo del hash de contraseñas con Argon2id.
 *
 * <p>Qué hace: calcula hashes reales y revisa su forma. Un hash Argon2 es un texto que
 * lleva escritos el algoritmo y sus parámetros, por ejemplo
 * {@code $argon2id$v=19$m=19456,t=2,p=1$<sal>$<hash>}; gracias a eso se puede comprobar aquí
 * con qué parámetros se calculó. No necesita Spring ni Docker.</p>
 */
class HasheadorArgon2Test {

    private static final String CONTRASENA = "una frase larga y dificil 2026";

    private final HasheadorArgon2 hasheador = new HasheadorArgon2();

    /**
     * El hash usa la variante Argon2id con los parámetros mínimos que recomienda OWASP
     * (organización de referencia en seguridad de aplicaciones): 19 MiB de memoria
     * (19456 KiB), 2 iteraciones y 1 hilo.
     */
    @Test
    void usa_argon2id_con_los_parametros_recomendados() {
        assertThat(hasheador.hashear(CONTRASENA)).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
    }

    /** El hash nunca contiene la contraseña. */
    @Test
    void el_hash_no_contiene_la_contrasena() {
        assertThat(hasheador.hashear(CONTRASENA)).doesNotContain(CONTRASENA);
    }

    /**
     * Dos hashes de la misma contraseña son distintos, porque cada cálculo añade un dato al
     * azar (la sal). Así dos personas con la misma contraseña no tienen el mismo hash.
     */
    @Test
    void dos_hashes_de_la_misma_contrasena_son_distintos() {
        assertThat(hasheador.hashear(CONTRASENA)).isNotEqualTo(hasheador.hashear(CONTRASENA));
    }

    /** El hash cabe en la columna donde se guarda, que admite 255 caracteres. */
    @Test
    void el_hash_cabe_en_la_columna() {
        String masLargaPermitida = "é".repeat(36);

        assertThat(hasheador.hashear(masLargaPermitida).length()).isLessThanOrEqualTo(255);
    }

    /**
     * El hash se puede verificar después con la herramienta estándar: la contraseña correcta
     * coincide y una distinta no. Es lo que hará el inicio de sesión.
     */
    @Test
    void el_hash_se_puede_verificar_despues() {
        Argon2PasswordEncoder verificador = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String hash = hasheador.hashear(CONTRASENA);

        assertThat(verificador.matches(CONTRASENA, hash)).isTrue();
        assertThat(verificador.matches("otra frase larga y dificil", hash)).isFalse();
    }

    /** Comparar la contraseña correcta con su propio hash da coincidencia. */
    @Test
    void la_contrasena_correcta_coincide_con_su_hash() {
        String hash = hasheador.hashear(CONTRASENA);

        assertThat(hasheador.coincide(CONTRASENA, hash)).isTrue();
    }

    /** Una contraseña distinta, aunque se parezca, no coincide. */
    @Test
    void una_contrasena_distinta_no_coincide() {
        String hash = hasheador.hashear(CONTRASENA);

        assertThat(hasheador.coincide(CONTRASENA + " ", hash)).isFalse();
        assertThat(hasheador.coincide("otra frase larga y dificil", hash)).isFalse();
    }

    /**
     * El hash de relleno (el de un valor aleatorio que nadie conoce) no coincide con una
     * contraseña cualquiera, y compararlo cuesta el mismo cálculo que con un hash real.
     */
    @Test
    void el_hash_de_relleno_no_coincide_con_una_contrasena_cualquiera() {
        String hashDeRelleno = hasheador.hashear("valor-inventado-que-hace-de-relleno");

        assertThat(hashDeRelleno).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(hasheador.coincide(CONTRASENA, hashDeRelleno)).isFalse();
    }

    /**
     * La contraseña se usa tal como llega: si lleva espacios en los extremos, esos espacios
     * forman parte de ella.
     */
    @Test
    void no_recorta_los_espacios_de_la_contrasena() {
        Argon2PasswordEncoder verificador = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String hash = hasheador.hashear("  " + CONTRASENA + "  ");

        assertThat(verificador.matches("  " + CONTRASENA + "  ", hash)).isTrue();
        assertThat(verificador.matches(CONTRASENA, hash)).isFalse();
    }
}