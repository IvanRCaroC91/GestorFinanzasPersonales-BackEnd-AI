-- =====================================================================
-- Servicio de identidad · proteger el historial de migraciones
--
-- Flyway anota en la tabla flyway_schema_history qué migraciones ya aplicó. Esa tabla se
-- crea antes de la primera migración, así que el permiso general que allí se concedió al
-- rol de la aplicación ("todas las tablas") la incluyó sin querer.
--
-- La aplicación no necesita esa tabla: solo la usa el rol que aplica las migraciones. Se
-- le retiran todos los permisos sobre ella para que un fallo o un ataque en el servicio
-- no pueda alterar qué migraciones constan como aplicadas.
--
-- Las tablas que se creen en migraciones futuras no heredan permisos: cada migración
-- debe conceder al rol de la aplicación los que necesite sobre sus tablas nuevas.
-- =====================================================================

REVOKE ALL ON flyway_schema_history FROM identidad_app;
