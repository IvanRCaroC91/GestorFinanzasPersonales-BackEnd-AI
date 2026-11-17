package com.smartkash.identidad.tokens.adaptadores;

import com.smartkash.identidad.tokens.puertos.CalculadorHuellas;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Calcula huellas con el algoritmo SHA-256.
 *
 * <p>Qué es: el adaptador que implementa el puerto {@link CalculadorHuellas}. No programa
 * el algoritmo: usa el que trae Java.</p>
 *
 * <p>Cómo se escribe la huella: SHA-256 produce 32 bytes, que aquí se escriben como 64
 * cifras hexadecimales en minúsculas. Es exactamente lo que cabe en las columnas de la base
 * de datos donde se guardan las huellas.</p>
 *
 * <p>Regla: el texto se lee siempre en UTF-8, para que el mismo texto dé la misma huella en
 * cualquier máquina, tenga la configuración de idioma que tenga.</p>
 */
@Component
class HuellasSha256 implements CalculadorHuellas {

    private static final String ALGORITMO = "SHA-256";

    /**
     * Calcula la huella SHA-256 de un texto.
     *
     * @param texto el texto del que se quiere la huella
     * @return la huella en 64 cifras hexadecimales en minúsculas
     * @throws NullPointerException si el texto es nulo
     */
    @Override
    public String huellaDe(String texto) {
        Objects.requireNonNull(texto, "El texto es obligatorio.");

        // Se pide un calculador nuevo en cada llamada: el objeto guarda estado mientras
        // calcula y no puede compartirse entre peticiones simultáneas.
        byte[] huella = calculador().digest(texto.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(huella);
    }

    private static MessageDigest calculador() {
        try {
            return MessageDigest.getInstance(ALGORITMO);
        } catch (NoSuchAlgorithmException imposible) {
            // Toda instalación de Java está obligada a incluir SHA-256. Si faltara, es un
            // fallo del entorno y no algo que el programa pueda resolver.
            throw new IllegalStateException("Esta instalación de Java no ofrece SHA-256.", imposible);
        }
    }
}