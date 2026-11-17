-- =====================================================================
-- Servicio de identidad · versión inicial de la política de tratamiento de datos
-- El registro exige aceptar una versión publicada de la política; sin esta fila
-- nadie podría crear una cuenta.
--
-- PROVISIONAL: el texto de esta versión es un borrador sin revisión legal y la dirección
-- es una ruta relativa, porque aún no existe la página pública. El texto definitivo debe
-- publicarse como una versión nueva en otra migración; esta fila no se modifica.
--
-- hash_texto es el SHA-256 del archivo politicas/politica-tratamiento-1.0.md, codificado
-- en UTF-8 y con saltos de línea de un solo carácter (LF). Sirve como prueba de qué texto
-- exacto aceptó cada persona.
-- =====================================================================

INSERT INTO politicas_tratamiento (version, publicada_en, url, hash_texto, cambia_finalidad)
VALUES ('1.0',
        TIMESTAMPTZ '2026-11-16 00:00:00+00',
        '/legal/politica-de-tratamiento/1.0',
        '9e4a589495707066b542b8c9681b2158c3e099f9ed2f38b878d0cabc5e9c2986',
        false);
