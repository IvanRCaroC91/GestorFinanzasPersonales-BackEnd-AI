package com.smartkash.finanzas.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos de configuración que necesita el servicio para validar los tokens de acceso.
 *
 * <p>Qué es {@code @ConfigurationProperties}: le pide a Spring que llene este record con los
 * valores que haya bajo {@code finanzas.seguridad} en la configuración. Spring convierte
 * {@code jwks-uri} (con guion) en el componente {@code jwksUri}.</p>
 *
 * <p>Regla: ningún valor puede faltar ni estar en blanco. Si falta, el servicio no arranca y
 * lo dice con claridad: aceptar tokens con una validación incompleta sería peor que no
 * arrancar.</p>
 *
 * @param jwksUri dirección donde el servicio de identidad publica sus claves públicas
 * @param emisor  valor que debe traer el dato {@code iss} de todo token; igual al que configura el servicio de identidad
 */

@ConfigurationProperties("finanzas.seguridad")
record PropiedadesDeSeguridad(String jwksUri, String emisor) {

    /**
     * Valida que los dos datos estén presentes.
     *
     * @throws IllegalStateException si falta alguno o está en blanco
     */

    PropiedadesDeSeguridad{
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException("Falta finanzas.seguridad.jwks-uri (variable FINANZAS_JWKS_URI)");
        }
        if (emisor == null || emisor.isBlank()) {
            throw new IllegalStateException("Falta finanzas.seguridad.emisor (variable FINANZAS_TOKEN_EMISOR)");
        }
    }
}
