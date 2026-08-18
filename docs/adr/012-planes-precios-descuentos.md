# ADR-012 — Planes de suscripción, precios y descuentos

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

Guardao cobra una suscripción mensual a cada barbería. El esquema inicial ya tiene la tabla `SUBSCRIPTION`, pero solo guarda `plan` (un texto), `status` y hasta cuándo está pagado el período. **No guarda cuánto cuesta ni qué habilita.**

Hay tres cosas por decidir antes de escribir el cobro recurrente (Etapa 5):

1. Cuáles son los planes y qué incluye cada uno.
2. Dónde vive el precio, sabiendo que "50.000 pesos" es el precio de **hoy** y va a subir.
3. Cómo se representan las ofertas: un descuento por venir con código de referido, o los primeros meses más baratos.

## Decisión

### 1. Tres planes

| | **Prueba** | **Básico** | **Profesional** |
|---|---|---|---|
| Precio | Gratis, 15 días | 50.000 COP/mes | 100.000 COP/mes |
| Sedes | 1 | 1 | hasta 5 |
| Barberos | hasta 3 | hasta 3 | sin límite |
| Agenda y reservas | ✅ | ✅ | ✅ |
| Página pública de reservas | ✅ | ✅ | ✅ |
| Cancelar y reprogramar por enlace | ✅ | ✅ | ✅ |
| Cobros con Wompi y adelantos | ✅ | ✅ | ✅ |
| Notificaciones por correo | ✅ | ✅ | ✅ |
| Notificaciones por WhatsApp | cupo de prueba | cupo mensual | cupo ampliado |
| Programa de lealtad | ✅ | — | ✅ |
| Catálogo de productos y carrito | ✅ | — | ✅ |
| Galería de Instagram y TikTok | ✅ | — | ✅ |
| Informes | completos | básicos | por barbero e ingresos |

La prueba incluye todo lo del plan Profesional. Una prueba recortada enseña un producto que nadie va a comprar.

### 2. Qué se limita y qué no

**Nunca se limita lo que vive el cliente final de la barbería.** Reservar, cancelar, reprogramar y recibir la confirmación funcionan igual en los tres planes. Quien reserva un corte no es cliente de Guardao y no tiene por qué notar en qué plan está la barbería.

Lo que sí separa los planes son tres cosas, en este orden de importancia:

- **El costo variable real.** WhatsApp se envía desde **una sola cuenta de Guardao** compartida por todas las barberías (ver Notificaciones en el plan). Cada mensaje lo paga Guardao. Es lo único que crece con el uso, así que es lo único que necesita un tope: sin él, una barbería con 800 citas al mes puede costar más de lo que paga.
- **El tamaño del negocio.** Sedes y barberos. Quien tiene tres sedes factura más y puede pagar más.
- **Las líneas adicionales.** Catálogo, galería y lealtad no son el servicio esencial: son formas de vender y retener que la barbería suma encima de las reservas.

### 3. El catálogo de planes vive en código; el precio pactado, en la suscripción

Los planes, con sus límites y su precio actual, se definen en código, igual que las paletas de tema de la página pública.

Pero el precio que se le cobra a una barbería **se copia a su suscripción** cuando se contrata:

```
SUBSCRIPTION {
  varchar plan            -- BASIC | PRO
  int     base_amount     -- lo que se pactó, en pesos
}
```

Es el mismo principio del [ADR-010](./010-snapshot-precio-duracion.md): el precio de hoy no debe reescribir el pasado ni el acuerdo vigente. Si mañana el Básico sube a 60.000, quien se suscribió a 50.000 sigue en 50.000 hasta que se le avise y se le cambie explícitamente. Sin la copia, subir una constante en el código le subiría el cobro a todos los clientes actuales de un despliegue para otro.

### 4. Los descuentos son un porcentaje con fecha de vencimiento

```
SUBSCRIPTION {
  smallint discount_percent      -- 10 = diez por ciento
  smallint discount_months_left  -- cuántos cobros más lo llevan
  varchar  discount_reason       -- REFERRAL | PROMO | MANUAL
}
```

Los tres campos van juntos: o los tres nulos, o los tres con valor. Una restricción `CHECK` lo sostiene, para que no quede un porcentaje sin vencimiento cobrando descuento para siempre.

Al generar el cobro mensual:

1. `monto = base_amount − (base_amount × discount_percent / 100)`
2. Se resta uno a `discount_months_left`.
3. Al llegar a cero, los tres campos se vacían y el siguiente cobro sale al precio normal.

**El descuento se apaga solo.** Nadie tiene que acordarse de quitarlo, que es exactamente el error que deja a un cliente con 10% de por vida.

Los motivos previstos:

| Motivo | Ejemplo |
|---|---|
| `REFERRAL` | Se registró con el código de otra barbería: 10% los primeros 2 meses |
| `PROMO` | Campaña de lanzamiento: 10% los primeros 2 meses |
| `MANUAL` | Negociación puntual, aplicada por un `SUPER_ADMIN` |

No hace falta una tabla de descuentos con historial: **`PAYMENT.amount` ya guarda lo que se cobró realmente cada mes**, que es lo que importa para la contabilidad. La suscripción solo necesita saber qué descuento está vigente ahora.

### 5. Qué pasa cuando la prueba termina o la suscripción se vence

La cuenta pasa a **solo lectura** y la página pública deja de aceptar reservas nuevas. Pero:

- Las citas ya agendadas siguen visibles para la barbería.
- Los enlaces de gestión que ya recibieron los clientes siguen funcionando para ver, cancelar o reprogramar.

Apagar la barbería por completo castigaría a sus clientes finales, que no tienen nada que ver con que el pago no entró. Y una cuenta que borra la agenda al vencer es una cuenta que nadie recupera.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Tabla de planes en la base de datos** | Cambiar un precio no debería ser una migración de datos. Y el plan no es solo una cifra: son límites que el código tiene que hacer cumplir de todos modos, así que la mitad quedaría en código igual. Se prefiere una sola fuente |
| **Precio solo en código, sin copia en la suscripción** | Subir la constante le cambiaría el cobro a todos los suscriptores actuales en el siguiente despliegue. Es el problema del [ADR-010](./010-snapshot-precio-duracion.md), aplicado al propio negocio de Guardao |
| **Tabla de descuentos con historial** | El historial de lo cobrado ya está en `PAYMENT`. Se reconsidera si algún día hay que acumular varios descuentos a la vez |
| **Limitar el número de reservas al mes** | Castiga a la barbería justo cuando le va bien, y la empuja a no registrar citas para no pasarse del cupo. Un límite que corrompe los datos que la plataforma necesita es peor que no tener límite |
| **Cobrar por barbero (precio por asiento)** | Más justo en teoría, pero convierte cada contratación en una conversación de precio y desincentiva registrar al personal. Con dos planes fijos, el dueño sabe qué paga |

## Consecuencias

**Ganamos**

- Cambiar un precio es una línea de código, y no toca a quien ya está suscrito
- El descuento vence solo, sin depender de que alguien lo recuerde
- El cupo de WhatsApp acota el único costo que escala con el uso
- Lo que ve el cliente final es igual en todos los planes

**Aceptamos**

- **Los límites hay que hacerlos cumplir en el código.** Crear una sede o un barbero tiene que consultar el plan; un endpoint que se olvide regala el plan Profesional. Es el mismo riesgo del [ADR-004](./004-aislamiento-multi-tenant.md) y merece el mismo trato: tests que intenten pasarse del límite
- **El cupo de WhatsApp exige contar mensajes.** La entidad `NOTIFICATION` ya existe y guarda cada envío, así que el conteo sale de ahí; falta decidir qué ocurre al agotarlo (¿cae a correo, o se avisa al dueño?)
- **Un referido cuesta dos veces.** Si el referido entra con 10% de descuento y además el referidor se lleva el 10% de esos pagos (Etapa 8), el margen de esa barbería baja cerca del 20% durante los meses de promoción. Puede estar bien como costo de adquisición, pero es una decisión de negocio, no un efecto que deba descubrirse leyendo un informe

## Pendiente de definir

Esto no bloquea el modelo, pero hay que cerrarlo antes de la Etapa 5:

- El número exacto de los cupos de WhatsApp por plan, que depende del precio por mensaje de Meta en Colombia
- Si el descuento por referido y la comisión al referidor conviven o si uno reemplaza al otro
- Si el plan Profesional lleva tope de 5 sedes o ninguno
