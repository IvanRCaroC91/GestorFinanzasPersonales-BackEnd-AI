package com.smartkash.identidad.tokens.adaptadores;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de {@link GeneradorTokensSeguro}.
 *
 * <p>Qué comprueban: que cada token es un valor aleatorio de 256 bits, escrito con
 * caracteres que pueden viajar en una dirección web o en una cookie sin transformarse, y que
 * no se repite.</p>
 *
 * <p>No necesitan Spring ni Docker.</p>
 */
class GeneradorTokensSeguroTest {

    private final GeneradorTokensSeguro generador = new GeneradorTokensSeguro();

    @Test
    @DisplayName("cada token contiene 32 bytes, es decir, 256 bits")
    void cadaTokenTiene256Bits() {
        byte[] bytes = Base64.getUrlDecoder().decode(generador.generar());

        assertEquals(32, bytes.length);
    }

    @Test
    @DisplayName("el token mide 43 caracteres y solo usa letras, cifras, guion y guion bajo")
    void elTokenSoloUsaCaracteresSegurosParaDirecciones() {
        String token = generador.generar();

        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]+"), "No debe llevar +, /, = ni espacios.");
    }

    @Test
    @DisplayName("el token cabe en el largo que admite el contrato: entre 20 y 200 caracteres")
    void elTokenCabeEnElContrato() {
        int largo = generador.generar().length();

        assertTrue(largo >= 20 && largo <= 200);
    }

    @Test
    @DisplayName("diez mil tokens seguidos son todos distintos")
    void losTokensNoSeRepiten() {
        Set<String> vistos = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            vistos.add(generador.generar());
        }

        assertEquals(10_000, vistos.size());
    }
}