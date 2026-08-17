# ADR-001 — Stack tecnológico: Spring Boot, Next.js y PostgreSQL

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

Proyecto nuevo, equipo de 4 desarrolladores, producto que debe llegar a MVP y sostenerse en producción con presupuesto de infraestructura bajo. Hay que elegir lenguaje de backend, framework de frontend y motor de base de datos.

## Decisión

- **Backend**: Spring Boot 4.1 sobre Java 21, API REST
- **Frontend**: Next.js 16 con App Router y React 19
- **Base de datos**: PostgreSQL 16
- **Persistencia**: Spring Data JPA con Hibernate; migraciones con Flyway
- **Autenticación**: Spring Security con JWT
- **Documentación de API**: springdoc-openapi (OpenAPI 3 y Swagger UI)

## Alternativas consideradas

### Backend

| Opción | A favor | En contra |
|---|---|---|
| **Spring Boot** | Ecosistema maduro; Spring Security resuelve auth sin inventar; transacciones declarativas sólidas; el equipo lo conoce | Más ceremonioso; arranque más lento |
| Node.js + NestJS | Un solo lenguaje con el frontend | Manejo transaccional más frágil, y las transacciones son críticas aquí |
| Django | Admin gratis, muy rápido de arrancar | El equipo no tiene experiencia en Python |
| Go | Consumo mínimo, muy rápido | Menos ecosistema para pagos y ORM; curva de aprendizaje |

Lo decisivo fue el **control transaccional**. El motor de reservas necesita revalidar disponibilidad e insertar dentro de la misma transacción, y manejar limpiamente la violación de una restricción de exclusión. Spring lo hace de forma declarativa y probada.

### Base de datos

PostgreSQL no fue una elección entre iguales: es un **requisito derivado** de la decisión de [ADR-003](./003-exclude-constraint-doble-reserva.md). Las restricciones `EXCLUDE` con `btree_gist` no existen en MySQL ni MariaDB. Sin ellas, no hay garantía real contra doble reserva.

Ventajas adicionales que confirman la elección: `jsonb` para el tema de la página pública, `CHECK` para el control de stock, y rangos de tiempo nativos.

### Frontend

| Opción | En contra |
|---|---|
| **Next.js** | — |
| React con Vite | Sin renderizado en servidor: la página pública de reservas necesita SEO y carga rápida en móvil |
| Astro | Excelente para contenido estático, pero el dashboard es una aplicación interactiva |

La página pública se abre desde celular, muchas veces con conexión mediocre, y debe indexarse. El renderizado en servidor de Next.js resuelve ambas cosas. Además permite inyectar el tema de colores del negocio sin parpadeo al cargar.

## Consecuencias

**Ganamos**
- Transacciones confiables, que es donde se juega la corrección del producto
- Spring Security evita implementar autenticación a mano
- Una sola página pública server-rendered, rápida e indexable
- Swagger generado desde el código: el frontend no adivina contratos

**Aceptamos**
- **Dos lenguajes en el equipo**: Java y TypeScript. Con 4 personas hay que evitar que se formen dos islas sin comunicación
- **Atados a PostgreSQL**. Migrar de motor implicaría rediseñar la protección contra doble reserva
- Spring Boot consume más memoria que alternativas ligeras — irrelevante en un VPS de 8 GB
- Next.js 16 es reciente: la documentación local en `node_modules/next/dist/docs/` manda sobre lo que se recuerde de versiones anteriores
