package com.smartkash.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Servidor de descubrimiento de SmartKash.
 *
 * <p>Es el registro central donde cada servicio publica su nombre y su dirección al arrancar, y
 * donde los demás lo consultan. El gateway no necesita conocer la dirección fija de cada
 * servicio: pregunta aquí por su nombre. Si un servicio cambia de máquina o de puerto, solo
 * hay que reiniciarlo y se vuelve a anunciar.
 *
 * <p>{@code @EnableEurekaServer} convierte esta aplicación en ese registro. Sin ella, arrancaría
 * como una aplicación web cualquiera.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaApplication.class, args);
    }
}