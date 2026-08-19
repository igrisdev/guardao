# Architecture Decision Records — Guardao

Registro de las decisiones técnicas del proyecto y por qué se tomaron.

## Para qué sirve esto

Dentro de seis meses alguien va a preguntar "¿por qué no usamos microservicios?" o "¿por qué cada barbería conecta su propio Wompi?". Sin este registro, la respuesta es una discusión desde cero. Con él, es leer dos páginas.

Un ADR no se edita cuando cambia la realidad: se escribe uno nuevo que marque al anterior como **Reemplazado**. El historial de decisiones equivocadas es tan útil como el de las acertadas.

## Índice

| ADR | Decisión | Estado |
|---|---|---|
| [001](./001-stack-tecnologico.md) | Stack: Spring Boot, Next.js, PostgreSQL | Aceptado |
| [002](./002-monolito-modular.md) | Monolito modular sobre microservicios | Aceptado |
| [003](./003-exclude-constraint-doble-reserva.md) | Restricción `EXCLUDE` contra doble reserva | Aceptado |
| [004](./004-aislamiento-multi-tenant.md) | Aislamiento multi-tenant por `business_id` | Aceptado |
| [005](./005-pasarela-por-negocio.md) | Cada negocio conecta su propia pasarela | Aceptado |
| [006](./006-cliente-sin-cuenta.md) | Cliente final sin registro, identificado por teléfono | Aceptado |
| [007](./007-flyway-sobre-ddl-auto.md) | Flyway con `ddl-auto: validate` | Aceptado |
| [008](./008-coolify-sobre-dokploy.md) | Coolify como plataforma de despliegue | Aceptado |
| [009](./009-sin-ia-en-el-producto.md) | Sin IA orientada al cliente final | Aceptado |
| [010](./010-snapshot-precio-duracion.md) | Snapshot de precio y duración en cada cita | Aceptado |
| [011](./011-jackson-3-en-spring-boot-4.md) | Jackson 3 como serializador JSON | Aceptado |
| [012](./012-planes-precios-descuentos.md) | Planes de suscripción, precios y descuentos | Aceptado |

## Plantilla

```markdown
# ADR-XXX — Título

**Estado:** Propuesto | Aceptado | Reemplazado por ADR-YYY
**Fecha:** AAAA-MM

## Contexto
Qué situación obliga a decidir.

## Decisión
Qué se decidió, en una frase.

## Alternativas consideradas
Qué más se evaluó y por qué se descartó.

## Consecuencias
Lo que ganamos y — sobre todo — lo que aceptamos perder.
```
