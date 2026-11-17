package com.smartkash.identidad.sesion.adaptadores.configuracion;

import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.sesion.aplicacion.IniciarSesion;
import com.smartkash.identidad.sesion.puertos.ConsultaDePoliticas;
import com.smartkash.identidad.sesion.puertos.EmisorTokenAcceso;
import com.smartkash.identidad.sesion.puertos.RepositorioBloqueos;
import com.smartkash.identidad.sesion.puertos.RepositorioTokensRenovacion;
import com.smartkash.identidad.tokens.puertos.CalculadorHuellas;
import com.smartkash.identidad.tokens.puertos.GeneradorTokens;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Crea el caso de uso del inicio de sesión y le entrega sus adaptadores.
 *
 * <p>Por qué hace falta: {@link IniciarSesion} no lleva anotaciones de Spring, para que sus
 * reglas no dependan del framework. Spring no lo encontraría solo, así que aquí se le dice cómo
 * crearlo. Cada parámetro de {@link #iniciarSesion} lo entrega Spring: son los adaptadores
 * reales (PostgreSQL, Argon2, la firma RS256...) que implementan los puertos.</p>
 *
 * <p>Se crea una sola vez al arrancar, y en ese momento se calcula el hash de relleno.</p>
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracionDeLaSesion {

    /**
     * Crea el caso de uso del inicio de sesión.
     *
     * @param usuarios          repositorio de cuentas
     * @param bloqueos          repositorio de contadores de fallos
     * @param tokensRenovacion  repositorio de tokens de renovación
     * @param emisorTokenAcceso firmador de tokens de acceso
     * @param hasheador         comparador de contraseñas
     * @param generador         generador de valores aleatorios
     * @param huellas           calculador de huellas
     * @param politicas         consulta de políticas pendientes
     * @param reloj             reloj del servicio, en UTC
     * @return el caso de uso listo para usarse
     */
    @Bean
    IniciarSesion iniciarSesion(
            RepositorioUsuarios usuarios,
            RepositorioBloqueos bloqueos,
            RepositorioTokensRenovacion tokensRenovacion,
            EmisorTokenAcceso emisorTokenAcceso,
            HasheadorContrasena hasheador,
            GeneradorTokens generador,
            CalculadorHuellas huellas,
            ConsultaDePoliticas politicas,
            Clock reloj) {
        return new IniciarSesion(usuarios, bloqueos, tokensRenovacion, emisorTokenAcceso,
                hasheador, generador, huellas, politicas, reloj);
    }
}