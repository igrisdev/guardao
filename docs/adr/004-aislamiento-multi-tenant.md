# ADR-004 — Aislamiento multi-tenant por `business_id`

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

Todas las barberías comparten la misma base de datos. Cada tabla de negocio cuelga, directa o indirectamente, de un `BUSINESS`. Una consulta a la que se le olvide filtrar por negocio expone datos de un cliente a otro: sus citas, sus clientes finales con teléfono y correo, y sus ingresos.

No es un fallo de funcionalidad, es una brecha de confidencialidad. Y bajo la Ley 1581 de 2012 (Habeas Data), también un problema legal.

## Decisión

**Base de datos compartida con filtrado obligatorio por `business_id`, aplicado por defecto.**

Tres capas:

1. **El JWT lleva el `business_id`** del usuario autenticado. No se acepta jamás como parámetro de la petición: un atacante lo cambiaría.
2. **Un filtro resuelve el tenant** al inicio de cada petición y lo deja en el contexto. Los repositorios lo aplican por defecto, no ruta por ruta.
3. **Tests de acceso cruzado** que intentan leer datos de otro negocio manipulando identificadores en la petición, y esperan un 403 o 404.

Los endpoints públicos (reservas por slug) son la excepción deliberada: no tienen JWT, y resuelven el negocio desde el slug de la URL. Por eso exponen **solo** lo que el cliente final necesita ver, nunca datos internos.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una base de datos por negocio** | Aísla mejor, pero multiplica migraciones, respaldos y conexiones por cada barbería. Inviable operativamente para un SaaS de bajo precio |
| **Un esquema de Postgres por negocio** | Punto intermedio, pero sigue complicando las migraciones y el pool de conexiones, y no aporta lo suficiente frente al filtrado |
| **Row Level Security de PostgreSQL** | Técnicamente elegante y muy sólida. Descartada porque añade una capa que el equipo no domina, y depurar por qué una fila "no aparece" se vuelve confuso. **Vale la pena reconsiderarla si el proyecto crece** |
| Filtrar manualmente en cada consulta | Es exactamente lo que produce la fuga: basta un olvido en un endpoint nuevo |

## Consecuencias

**Ganamos**
- Una sola base de datos: migraciones simples, respaldos simples, operación simple
- El filtrado ocurre por defecto; un endpoint nuevo lo hereda sin que el desarrollador se acuerde

**Aceptamos**
- **La garantía es de código, no del motor.** A diferencia de la restricción `EXCLUDE` del [ADR-003](./003-exclude-constraint-doble-reserva.md), aquí no hay una red de seguridad en la base de datos. Si alguien escribe una consulta nativa saltándose el filtro, la fuga es posible
- Por eso los tests de acceso cruzado **no son opcionales** y toda consulta nativa (`@Query` con SQL) exige revisión explícita en el PR
- Un error de programación en esta capa es crítico, no cosmético

## Regla para el equipo

> Cualquier PR que agregue una consulta SQL nativa o un repositorio nuevo debe demostrar, en su descripción, cómo respeta el aislamiento por `business_id`. Sin eso, no se aprueba.
