package com.smartkash.identidad.tokens.adaptadores;

import com.smartkash.identidad.tokens.puertos.GeneradorTokens;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Genera tokens de 256 bits con el generador de azar para seguridad de Java.
 *
 * <p>Qué es: el adaptador que implementa el puerto {@link GeneradorTokens}.</p>
 *
 * <p>Por qué {@code SecureRandom}: los generadores de azar corrientes producen secuencias
 * que se pueden predecir conociendo algunos valores anteriores. {@code SecureRandom} toma
 * su azar del sistema operativo y está pensado para que eso no sea posible.</p>
 *
 * <p>Cómo se escribe el valor: los 32 bytes se pasan a texto con Base64 en su variante para
 * direcciones web, que usa solo letras, cifras, guion y guion bajo, y sin relleno al final.
 * El resultado mide siempre 43 caracteres.</p>
 *
 * <p>A dónde van los datos: al caso de uso que pidió el token. Esta clase no guarda ni
 * escribe en logs ningún valor generado.</p>
 */
@Component
class GeneradorTokensSeguro implements GeneradorTokens {

    /** Tamaño de cada token, en bytes: 256 bits. */
    private static final int BYTES = 32;

    /* Una sola instancia sirve para todas las peticiones a la vez. */
    private final SecureRandom azar = new SecureRandom();

    private final Base64.Encoder codificador = Base64.getUrlEncoder().withoutPadding();

    /**
     * Genera un valor nuevo de 256 bits.
     *
     * @return el valor en claro, de 43 caracteres, distinto en cada llamada
     */
    @Override
    public String generar() {
        byte[] bytes = new byte[BYTES];
        azar.nextBytes(bytes);
        return codificador.encodeToString(bytes);
    }
}