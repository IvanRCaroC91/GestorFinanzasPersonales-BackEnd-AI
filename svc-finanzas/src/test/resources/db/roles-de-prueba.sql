-- Solo para pruebas. Crea el rol con el que se conecta la aplicacion, porque la primera
-- migracion le concede permisos y fallaria si el rol no existiera.
-- En un entorno real este rol lo crea quien administra la base de datos, con su contrasena
-- guardada como secreto. Aqui no lleva contrasena ni puede iniciar sesion: nadie se conecta
-- con el, solo hace falta que exista.
CREATE ROLE finanzas_app NOLOGIN;