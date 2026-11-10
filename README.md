# SmartKash Backend

Backend del Gestor de Finanzas Personales (TFM UNIR). Maven multimódulo con Java 25 y Spring Boot 4.1.

## Módulos
- `nucleo-financiero`: tipos compartidos sin framework (Dinero, TasaInteres, Periodo).
- `nucleo-web`: errores RFC 9457 e idempotencia.

## Compilar
./mvnw verify