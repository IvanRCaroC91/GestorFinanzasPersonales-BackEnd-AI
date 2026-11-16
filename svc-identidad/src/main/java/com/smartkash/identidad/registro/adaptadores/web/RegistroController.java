package com.smartkash.identidad.registro.adaptadores.web;

import com.smartkash.identidad.registro.aplicacion.RegistrarUsuario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puerta de entrada web del registro de usuarios.
 *
 * <p>Qué es: un controlador. Convierte una petición HTTP en una llamada al caso de uso y el
 * resultado en una respuesta HTTP. No contiene reglas de negocio.</p>
 *
 * <p>De dónde vienen los datos: del cuerpo JSON de {@code POST /api/v1/auth/registro}, una
 * ruta pública. A dónde van: al caso de uso {@link RegistrarUsuario}.</p>
 *
 * <p>Regla clave: la respuesta es siempre la misma, exista o no una cuenta con ese correo.
 * Por eso el método no mira qué ocurrió dentro del caso de uso y devuelve un mensaje fijo.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
class RegistroController {

    /** Único mensaje que devuelve el registro, se haya creado la cuenta o no. */
    static final String MENSAJE = "Hemos recibido tu solicitud de registro.";

    private final RegistrarUsuario registrarUsuario;

    /**
     * @param registrarUsuario el caso de uso del registro; lo entrega Spring
     */
    RegistroController(RegistrarUsuario registrarUsuario) {
        this.registrarUsuario = registrarUsuario;
    }

    /**
     * Recibe una solicitud de registro.
     *
     * <p>Responde 202 ("aceptada"), y no 201 ("creada"), porque no confirma que se haya
     * creado nada: solo que la solicitud se recibió.</p>
     *
     * @param solicitud el cuerpo de la petición; el servicio rechaza con 400 los cuerpos que
     *                  no son JSON válido o traen campos no declarados
     * @return el mensaje genérico, idéntico para un correo nuevo y para uno ya registrado
     */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.ACCEPTED)
    MensajeGenerico registrar(@RequestBody RegistroSolicitud solicitud) {
        registrarUsuario.ejecutar(solicitud.aDatos());
        return new MensajeGenerico(MENSAJE);
    }
}
