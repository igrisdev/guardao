# ADR-007 — Flyway con `ddl-auto: validate`

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

Hibernate puede generar y actualizar el esquema solo (`ddl-auto: update`). Con 4 desarrolladores trabajando en paralelo y una base de datos en producción, hay que decidir quién manda sobre el esquema.

## Decisión

**El esquema lo define Flyway. Hibernate solo valida.**

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

Toda modificación de esquema es un archivo de migración versionado en el repositorio. Hibernate arranca comparando las entidades contra el esquema real y **falla al iniciar** si no coinciden.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`ddl-auto: update`** | Hibernate no borra columnas ni renombra: acumula basura silenciosamente. No es reproducible entre entornos, y en producción es directamente peligroso |
| **`ddl-auto: create-drop`** | Solo sirve para pruebas descartables |
| **Liquibase** | Equivalente en capacidad. Flyway se eligió por SQL plano: cualquiera del equipo lee una migración sin aprender un formato intermedio |

Hay un argumento adicional decisivo: la restricción `EXCLUDE` del [ADR-003](./003-exclude-constraint-doble-reserva.md), la extensión `btree_gist` y el `CHECK (stock >= 0)` **no los genera Hibernate**. Son SQL que alguien tiene que escribir. Con `ddl-auto: update`, esas garantías simplemente no existirían.

## Consecuencias

**Ganamos**
- El esquema es reproducible: cualquier entorno se levanta desde cero, idéntico
- Las migraciones se revisan en el PR como cualquier otro código
- `validate` detecta al arrancar cuando una entidad y su tabla se desalinean, en vez de fallar en tiempo de ejecución
- Se pueden escribir restricciones que ningún ORM genera

**Aceptamos**
- Cada cambio de modelo exige escribir la migración a mano. Es trabajo extra, y es precisamente el punto
- **Una migración ya mergeada a `develop` nunca se modifica**: Flyway guarda un checksum y falla si cambia. Corregir algo implica una migración nueva
- En local, resolver un conflicto de migraciones suele ser más rápido con base limpia (`docker compose down -v`) — está documentado en el README

## Regla para el equipo

> Nunca modifiques un archivo de migración que ya esté en `develop`. Si algo está mal, crea una migración nueva que lo corrija.
