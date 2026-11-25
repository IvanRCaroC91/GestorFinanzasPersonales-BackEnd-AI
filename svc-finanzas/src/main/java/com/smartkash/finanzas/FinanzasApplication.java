package com.smartkash.finanzas;

import com.smartkash.nucleo.web.errores.ManejadorErroresComun;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Servicio de finanzas de SmartKash.
 *
 * <p>Guarda y consulta lo que el usuario hace con su dinero: cuentas, categorías y
 * movimientos. No sabe quién es el usuario por sí mismo: confía en el token de acceso que emite
 * el servicio de identidad y del que toma el identificador de la persona.
 *
 * <p>{@code @SpringBootApplication} reúne tres cosas: marca esta clase como configuración,
 * activa la configuración automática según las dependencias presentes y escanea este paquete y
 * los de abajo en busca de piezas que Spring deba construir.
 *
 * <p>{@code @Import} añade el manejador de errores común de {@code nucleo-web}, que vive fuera
 * de este paquete y por eso Spring no lo encontraría solo. Convierte en respuestas estándar los
 * datos mal formados y los fallos inesperados.
 */

@SpringBootApplication
@Import(ManejadorErroresComun.class)
public class FinanzasApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanzasApplication.class, args);
    }

}
