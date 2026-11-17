package com.smartkash.identidad.sesion.adaptadores.seguridad;

import com.smartkash.identidad.sesion.puertos.EmisorTokenAcceso;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Prepara al arrancar la clave de firma y el emisor de tokens de acceso.
 *
 * <p>Qué es {@code @Configuration}: una clase cuyos métodos {@code @Bean} le dicen a Spring
 * cómo crear ciertos objetos. Spring los llama una vez al arrancar y guarda el resultado para
 * entregarlo a quien lo pida.</p>
 *
 * <p>Regla: el servicio no arranca sin clave de firma ni emisor. Es preferible que no arranque
 * a que arranque y no pueda iniciar sesiones, o que firme con una clave improvisada. Los
 * mensajes de error nombran la variable de entorno que hay que definir.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesDelToken.class)
class ConfiguracionDeLaFirma {

    /**
     * Lee la clave de firma del archivo indicado en la configuración.
     *
     * @param propiedades la configuración de los tokens
     * @return la clave, ya comprobada
     * @throws IllegalStateException si falta la ruta o el archivo no sirve
     */
    @Bean
    ClaveDeFirma claveDeFirma(PropiedadesDelToken propiedades) {
        String ruta = propiedades.claveFirmaRuta();
        if (ruta == null || ruta.isBlank()) {
            throw new IllegalStateException("Falta la clave de firma de los tokens: define la "
                    + "variable de entorno IDENTIDAD_CLAVE_FIRMA_RUTA con la ruta del archivo PEM.");
        }
        return ClaveDeFirma.desdeArchivo(Path.of(ruta));
    }

    /**
     * Crea el emisor de tokens de acceso que usará el inicio de sesión.
     *
     * @param clave       la clave de firma
     * @param propiedades la configuración de los tokens
     * @return el emisor, que implementa el puerto {@link EmisorTokenAcceso}
     * @throws IllegalStateException si falta el emisor
     */
    @Bean
    EmisorTokenAcceso emisorTokenAcceso(ClaveDeFirma clave, PropiedadesDelToken propiedades) {
        String emisor = propiedades.emisor();
        if (emisor == null || emisor.isBlank()) {
            throw new IllegalStateException("Falta el emisor de los tokens: define la variable "
                    + "de entorno IDENTIDAD_TOKEN_EMISOR.");
        }
        return new EmisorJwtRs256(clave, emisor);
    }
}
