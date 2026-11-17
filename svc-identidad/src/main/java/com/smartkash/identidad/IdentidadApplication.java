package com.smartkash.identidad;

import org.springframework.boot.SpringApplication;
import com.smartkash.nucleo.web.errores.ManejadorErroresComun;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Punto de arranque del servicio de identidad.
 *
 * <p>Qué hace: inicia la aplicación Spring Boot. Al arrancar, Spring busca todas las
 * clases del paquete {@code com.smartkash.identidad} y de sus subpaquetes (controladores,
 * casos de uso, adaptadores) y las conecta entre sí.</p>
 *
 * <p>Responsabilidad del servicio: gestionar a las personas que usan la aplicación, es
 * decir, el registro, el inicio de sesión, la emisión de tokens de acceso y los roles.
 * Las peticiones llegan desde el gateway, que es la única puerta de entrada al sistema.</p>
 *
 * <p>{@code @Import} añade el manejador de errores común, que vive en una librería
 * compartida fuera del paquete de este servicio y por eso Spring no lo encontraría solo.</p>
 */
@SpringBootApplication
@Import(ManejadorErroresComun.class)
public class IdentidadApplication {

    /**
     * Constructor vacío. Spring lo usa para crear la clase de arranque; no hace nada más.
     */
    public IdentidadApplication() {
        // Sin trabajo propio: toda la configuración la aportan las anotaciones de la clase.
    }

    /**
     * Arranca el servicio.
     *
     * @param args argumentos de línea de comandos; Spring los usa para sobrescribir
     *             configuración, por ejemplo el puerto
     */
    public static void main(String[] args) {
        // Crea el contexto de Spring (el contenedor que administra los objetos del servicio)
        // y levanta el servidor web embebido que atiende las peticiones HTTP.
        SpringApplication.run(IdentidadApplication.class, args);
    }
}
