package com.smartkash.identidad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 */
@SpringBootApplication
public class IdentidadApplication {

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
