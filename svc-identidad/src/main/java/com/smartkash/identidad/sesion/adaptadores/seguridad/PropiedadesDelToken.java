package com.smartkash.identidad.sesion.adaptadores.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de los tokens de acceso, leída de {@code application.yml}.
 *
 * <p>Qué es {@code @ConfigurationProperties}: le pide a Spring que llene este record con las
 * propiedades que empiezan por {@code identidad.token}. Por ejemplo,
 * {@code identidad.token.clave-firma-ruta} llena {@link #claveFirmaRuta}. En
 * {@code application.yml} esos valores vienen de variables de entorno, así que en el
 * repositorio no hay ninguno.</p>
 *
 * <p>Los valores pueden llegar vacíos si falta la variable; quien los usa lo comprueba y
 * detiene el arranque con un mensaje que nombra la variable.</p>
 *
 * @param emisor         valor de {@code iss} de los tokens; viene de
 *                       {@code IDENTIDAD_TOKEN_EMISOR}
 * @param claveFirmaRuta ruta del archivo PEM con la clave privada; viene de
 *                       {@code IDENTIDAD_CLAVE_FIRMA_RUTA}
 */
@ConfigurationProperties("identidad.token")
record PropiedadesDelToken(String emisor, String claveFirmaRuta) {
}