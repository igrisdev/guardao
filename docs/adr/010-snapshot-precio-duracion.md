# ADR-010 — Snapshot de precio y duración en cada cita

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

Una cita apunta a un servicio, y el servicio tiene precio y duración. La forma natural en un modelo relacional es leer esos valores del servicio cuando se necesiten.

El problema aparece con el tiempo: si en marzo la barbería sube el corte de 25.000 a 30.000 pesos, **todas las citas de febrero pasarían a valer 30.000** en los informes. Los ingresos históricos se reescriben solos.

Lo mismo con la duración: cambiar un servicio de 30 a 45 minutos alteraría el rango de tiempo de citas ya agendadas, y eso puede disparar la restricción `EXCLUDE` del [ADR-003](./003-exclude-constraint-doble-reserva.md) sobre citas que ya convivían sin problema.

## Decisión

`APPOINTMENT` guarda **su propia copia** de `price` y `duration_min`, copiados del servicio en el momento de crear la cita.

```
APPOINTMENT {
  uuid service_id FK     -- qué servicio fue
  int price              -- cuánto costó ese día
  int duration_min       -- cuánto duraba ese día
}
```

La relación con `SERVICE` se conserva para saber **qué** se hizo. Los valores copiados registran **en qué condiciones** se hizo.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Leer siempre del servicio** | Reescribe la historia cada vez que cambia un precio. Inaceptable para informes de ingresos |
| **Versionar los servicios** (una fila nueva por cada cambio) | Correcto en teoría, pero complica el CRUD, la interfaz y las consultas para un beneficio que dos columnas ya resuelven |
| **Tabla de historial de precios** | Misma complejidad, mismo resultado, más código |

## Consecuencias

**Ganamos**
- Los informes históricos son estables: lo que se cobró en febrero sigue diciendo lo que se cobró en febrero
- Cambiar precios es seguro y no requiere avisos ni migraciones
- La duración de una cita agendada no cambia bajo los pies del motor de disponibilidad

**Aceptamos**
- Duplicación deliberada de datos. **No es una violación de la normalización**: son cosas distintas — el precio actual del servicio y el precio cobrado en esa cita
- Si alguien corrige un precio mal digitado, las citas ya creadas conservan el valor erróneo. Es el comportamiento correcto para un registro contable, pero puede confundir a quien no lo espere
- Hay que acordarse de copiar ambos campos al crear la cita **y también al reprogramar**, si en el futuro la reprogramación permite cambiar de servicio

## Aplicación equivalente en el catálogo

El mismo principio aplica a `ITEM` (líneas de pedido de productos), que copia el `unit_price` del producto al momento de la compra. Misma razón, mismo beneficio.
