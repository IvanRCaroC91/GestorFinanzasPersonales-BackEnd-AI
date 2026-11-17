package com.smartkash.identidad.verificacion.adaptadores.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la verificación del correo, leída de {@code application.yml}.
 *
 * @param direccionBase dirección de la web a la que apuntan los enlaces de los correos, por
 *                      ejemplo {@code https://app.ejemplo}; viene de {@code IDENTIDAD_ENLACE_BASE}
 */
@ConfigurationProperties("identidad.verificacion")
record PropiedadesDeLaVerificacion(String direccionBase) {
}