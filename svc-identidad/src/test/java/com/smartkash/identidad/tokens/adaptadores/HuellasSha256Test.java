package com.smartkash.identidad.tokens.adaptadores;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de {@link HuellasSha256}.
 *
 * <p>Qué comprueban: que la huella es el SHA-256 del texto, escrito como 64 cifras
 * hexadecimales en minúsculas, que es justo lo que cabe en las columnas donde se guarda.
 * SHA-256 es un algoritmo público: los valores esperados de estas pruebas se calcularon con
 * una herramienta independiente, no con el código que se prueba.</p>
 *
 * <p>No necesitan Spring ni Docker.</p>
 */
class HuellasSha256Test {

    private final HuellasSha256 huellas = new HuellasSha256();

    @Test
    @DisplayName("calcula el SHA-256 conocido del texto abc")
    void calculaElValorDeReferencia() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                huellas.huellaDe("abc"));
    }

    @Test
    @DisplayName("lee el texto en UTF-8, así una eñe da la misma huella en cualquier máquina")
    void usaUtf8() {
        assertEquals("43d3606a2f5de3e1efcc843bb5ac78324619f5ee27b2020fa549cbe5616ff575",
                huellas.huellaDe("CUENTA:año@correo.com"));
    }

    @Test
    @DisplayName("la huella mide 64 caracteres y usa solo cifras y letras de la a a la f")
    void laHuellaTieneLaFormaEsperada() {
        assertTrue(huellas.huellaDe("cualquier texto").matches("[0-9a-f]{64}"));
        assertTrue(huellas.huellaDe("").matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("el mismo texto da siempre la misma huella y textos distintos dan huellas distintas")
    void esEstableYDistingueTextos() {
        assertEquals(huellas.huellaDe("IP:203.0.113.7"), huellas.huellaDe("IP:203.0.113.7"));
        assertNotEquals(huellas.huellaDe("IP:203.0.113.7"), huellas.huellaDe("IP:203.0.113.8"));
    }

    @Test
    @DisplayName("exige un texto")
    void exigeUnTexto() {
        assertThrows(NullPointerException.class, () -> huellas.huellaDe(null));
    }
}