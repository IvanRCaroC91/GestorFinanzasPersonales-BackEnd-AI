package com.smartkash.finanzas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 */

@SpringBootApplication
public class FinanzasApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanzasApplication.class, args);
    }

}
