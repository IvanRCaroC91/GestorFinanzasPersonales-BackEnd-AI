package com.smartkash.identidad.registro.puertos;

/**
 * Lista de contraseñas demasiado conocidas como para permitirlas (por ejemplo, las que
 * aparecen en filtraciones públicas).
 *
 * <p>Qué es: un puerto, es decir, una interfaz que describe lo que las reglas de negocio
 * necesitan del exterior sin decir cómo se consigue. Las reglas solo preguntan "¿esta
 * contraseña es común?"; de dónde sale la lista (un archivo, memoria, otro sistema) lo
 * decide la clase que implemente esta interfaz.</p>
 *
 * <p>De dónde vienen los datos: quien pregunta es la política de contraseñas durante el
 * registro. A dónde van: la respuesta solo sirve para aceptar o rechazar la contraseña;
 * no se guarda.</p>
 *
 * <p>Tiene un solo método, por eso en las pruebas puede sustituirse por una función de
 * una línea en lugar de una clase completa.</p>
 */
@FunctionalInterface
public interface ContrasenasComunes {

    /**
     * Indica si la contraseña está en la lista de contraseñas comunes.
     *
     * <p>Reglas: quien llama entrega la contraseña ya en minúsculas (convertida sin
     * depender del idioma de la máquina), y la lista debe guardar sus entradas también
     * en minúsculas. Así la comparación no distingue mayúsculas. La implementación no
     * debe escribir la contraseña en logs ni guardarla.</p>
     *
     * @param contrasenaEnMinusculas la contraseña a consultar, en minúsculas; nunca nula
     * @return {@code true} si está en la lista y por tanto debe rechazarse
     */
    boolean contiene(String contrasenaEnMinusculas);
}
