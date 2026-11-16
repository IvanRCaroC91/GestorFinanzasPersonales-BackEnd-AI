package com.smartkash.identidad.registro.adaptadores.web;

import com.smartkash.identidad.registro.aplicacion.DatosDeRegistro;
import com.smartkash.identidad.registro.dominio.PoliticaContrasena;
import com.smartkash.identidad.registro.dominio.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la solicitud de registro, tal como llega en JSON.
 *
 * <p>Qué es: un record, es decir, una clase que solo agrupa datos y no cambia después de
 * crearse. Spring lo llena con el JSON de la petición: cada campo del JSON va al componente
 * del mismo nombre.</p>
 *
 * <p>De dónde vienen los datos: del cuerpo de la petición que envía el frontend. A dónde
 * van: se copian a {@link DatosDeRegistro} para el caso de uso, con {@link #aDatos()}.</p>
 *
 * <p>Reglas de forma: las anotaciones sobre cada componente ({@code @NotBlank},
 * {@code @Email}...) dicen qué debe cumplir el dato para considerarse bien formado: que
 * esté presente y, en el correo, que tenga forma de correo. Spring las comprueba antes de
 * llamar al controlador y, si alguna falla, responde con la lista de campos inválidos.</p>
 *
 * <p>Qué no comprueba: las reglas de negocio. El largo exacto del nombre y de la contraseña
 * (aquí solo hay un tope holgado contra valores desmesurados), la lista de contraseñas comunes y que la política esté aceptada y sea la vigente los decide
 * el caso de uso, con las clases del dominio.</p>
 *
 * <p>Campos protegidos: declara solo los cinco campos que la persona puede decidir. No hay
 * rol, estado ni identificador, así que una petición no puede elegirlos; además, el servicio
 * rechaza cualquier petición que traiga un campo no declarado aquí.</p>
 *
 * @param nombre          nombre para mostrar
 * @param correo          correo como lo escribió la persona
 * @param contrasena      contraseña en claro; solo se recibe, nunca se devuelve
 * @param aceptaPolitica  si la persona aceptó la política de tratamiento de datos
 * @param versionPolitica versión de la política que vio la persona
 */
record RegistroSolicitud(

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = RegistroSolicitud.TOPE_NOMBRE,
                message = "El nombre no puede tener más de 100 caracteres.")
        String nombre,

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 254, message = "El correo no puede tener más de 254 caracteres.")
        String correo,

        @NotNull(message = "La contraseña es obligatoria.")
        @Size(max = RegistroSolicitud.TOPE_CONTRASENA,
                message = "La contraseña no puede tener más de 64 caracteres.")
        String contrasena,

        @NotNull(message = "Debes indicar si aceptas la política de tratamiento de datos.")
        Boolean aceptaPolitica,

        @NotBlank(message = "La versión de la política es obligatoria.")
        @Size(max = 10, message = "La versión de la política no puede tener más de 10 caracteres.")
        String versionPolitica) {

    /*
     * Topes de tamaño para frenar valores desmesurados en la puerta, antes de gastar un
     * cálculo de hash. No sustituyen a los largos exactos del dominio (100 y 64 caracteres
     * como los ve la persona): aquí se mide en unidades internas de Java, donde un emoji
     * ocupa dos, así que el tope es el doble para no rechazar nunca un valor válido.
     */
    static final int TOPE_NOMBRE = 2 * Usuario.LARGO_MAXIMO_NOMBRE;
    static final int TOPE_CONTRASENA = 2 * PoliticaContrasena.LARGO_MAXIMO;

    /**
     * Copia los datos al objeto que entiende el caso de uso.
     *
     * @return los datos de registro, sin transformar la contraseña ni el correo
     */
    DatosDeRegistro aDatos() {
        return new DatosDeRegistro(
                nombre, correo, contrasena, Boolean.TRUE.equals(aceptaPolitica), versionPolitica);
    }

    /**
     * Devuelve un texto para depuración que no revela nada sensible.
     *
     * <p>Un record normalmente muestra todos sus campos; aquí se omiten la contraseña y los
     * datos personales para que no terminen escritos en un log por accidente.</p>
     *
     * @return texto con lo único que es seguro mostrar
     */
    @Override
    public String toString() {
        return "RegistroSolicitud[aceptaPolitica=" + aceptaPolitica
                + ", versionPolitica=" + versionPolitica + "]";
    }
}
