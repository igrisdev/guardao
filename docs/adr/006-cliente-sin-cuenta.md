# ADR-006 — El cliente final no tiene cuenta

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

El cliente que reserva un corte necesita poder ver, cancelar y reprogramar su cita. La forma habitual de resolverlo es con registro y login.

## Decisión

**El cliente final no se registra.** Se le identifica por número de teléfono dentro de cada negocio, y gestiona su cita mediante un **enlace privado con token único** que recibe por WhatsApp o correo.

- Al reservar, el sistema busca un `CLIENT` por `(business_id, phone)`. Si no existe, lo crea.
- Cada `APPOINTMENT` genera un `manage_token` aleatorio y largo.
- Con ese enlace el cliente ve el detalle, cancela o reprograma. Reprogramar pasa por la misma validación de disponibilidad que una reserva nueva.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Registro con correo y contraseña** | Fricción alta para una acción de un minuto. Muchos abandonan la reserva antes de terminar |
| **Login por código de un solo uso al teléfono** | Menos fricción que una contraseña, pero agrega un paso y un costo de SMS por cada reserva |
| **Login social (Google)** | Sigue siendo un paso extra, y no todos los clientes finales tienen o quieren usar cuenta de Google para reservar un corte |

El razonamiento de producto es simple: **cada campo que se le pide a alguien para cortarse el pelo cuesta reservas.**

## Consecuencias

**Ganamos**
- La reserva se completa en menos de 90 segundos, meta del PRD
- Sin pantallas de registro, recuperación de contraseña ni verificación de correo que construir y mantener
- El cliente recurrente es reconocido automáticamente por su teléfono, sin que él haga nada

**Aceptamos**
- **El `manage_token` es la única credencial que protege esa cita.** Debe ser criptográficamente aleatorio y largo; si es predecible, cualquiera puede cancelar citas ajenas. Los tests de enumeración del token son obligatorios (GUA-62)
- El teléfono es la clave de identidad. Si un cliente lo digita mal, se crea un registro duplicado — se acepta como costo menor
- Un cliente no puede ver "todas sus citas" en un solo lugar: cada cita tiene su enlace. Es una limitación consciente
- Los endpoints públicos necesitan **rate limiting** para que nadie intente adivinar tokens por fuerza bruta ni sature la agenda con reservas falsas

## Nota de seguridad

El enlace de gestión viaja por WhatsApp o correo. Quien tenga acceso a ese mensaje puede gestionar la cita. Es un compromiso aceptable dado lo que está en juego (una cita de barbería), pero **este modelo no sería aceptable** para el vertical de restaurantes o discotecas si llegara a manejar pagos de mayor valor. Revisar en ese momento.
