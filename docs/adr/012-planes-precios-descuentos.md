# ADR-012 — Planes de suscripción, precios y descuentos

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

Guardao cobra una suscripción mensual a cada barbería. El esquema inicial ya tiene la tabla `SUBSCRIPTION`, pero solo guarda `plan` (un texto), `status` y hasta cuándo está pagado el período. **No guarda cuánto cuesta ni qué habilita.**

Hay cuatro cosas por decidir antes de escribir el cobro recurrente (Etapa 5):

1. Cuáles son los planes y qué incluye cada uno.
2. Dónde vive el precio, sabiendo que "50.000 pesos" es el precio de **hoy** y va a subir.
3. Cómo se representan las ofertas: venir con código de referido, o una campaña de lanzamiento.
4. De dónde salen esos códigos y quién los crea.

## Decisión

### 1. Cinco planes

| | **Prueba** | **Inicial** | **Básico** | **Profesional** | **Empresarial** |
|---|---|---|---|---|---|
| Precio | Gratis, 15 días | 25.000 COP | 50.000 COP | 100.000 COP | Desde 120.000, a convenir |
| Cómo se contrata | Solo | Solo | Solo | Solo | Hablando con Guardao |
| Sedes | 1 | 1 | 1 | hasta 5 | a convenir |
| Barberos | hasta 3 | hasta 2 | hasta 6 | sin límite | sin límite |
| Agenda y reservas | ✅ | ✅ | ✅ | ✅ | ✅ |
| Página pública de reservas | ✅ | ✅ | ✅ | ✅ | ✅ |
| Cancelar y reprogramar por enlace | ✅ | ✅ | ✅ | ✅ | ✅ |
| Cobros con Wompi y adelantos | ✅ | ✅ | ✅ | ✅ | ✅ |
| Notificaciones por correo | ✅ | ✅ | ✅ | ✅ | ✅ |
| Notificaciones por WhatsApp | cupo de prueba | cupo bajo | cupo medio | cupo alto | a convenir |
| Programa de lealtad | ✅ | — | ✅ | ✅ | ✅ |
| Catálogo de productos y carrito | ✅ | — | — | ✅ | ✅ |
| Galería de Instagram y TikTok | ✅ | — | — | ✅ | ✅ |
| Informes | completos | básicos | básicos | por barbero e ingresos | a convenir |

La prueba incluye todo lo del plan Profesional. Una prueba recortada enseña un producto que nadie va a comprar.

**Inicial** es para el barbero que trabaja solo o con un ayudante: la agenda y la página de reservas completas, sin las líneas de venta adicionales. **Básico** es la barbería de barrio con equipo. **Profesional** es quien ya tiene varias sedes o vende productos.

### 2. Qué se limita y qué no

**Nunca se limita lo que vive el cliente final de la barbería.** Reservar, cancelar, reprogramar y recibir la confirmación funcionan igual en los cinco planes. Quien reserva un corte no es cliente de Guardao y no tiene por qué notar en qué plan está la barbería.

Lo que sí separa los planes son tres cosas, en este orden de importancia:

- **El costo variable real.** WhatsApp se envía desde **una sola cuenta de Guardao** compartida por todas las barberías (ver Notificaciones en el plan). Cada mensaje lo paga Guardao. Es lo único que crece con el uso, así que es lo único que necesita un tope: sin él, una barbería con 800 citas al mes puede costar más de lo que paga.
- **El tamaño del negocio.** Sedes y barberos. Quien tiene tres sedes factura más y puede pagar más.
- **Las líneas adicionales.** Catálogo, galería y lealtad no son el servicio esencial: son formas de vender y retener que la barbería suma encima de las reservas.

### 3. El plan Empresarial no se contrata solo

Desde 120.000 pesos, pero el precio y los límites **se acuerdan hablando**. No hay botón que lo contrate: quien tiene ocho sedes necesita una conversación, no un formulario.

Eso tiene una consecuencia en el modelo. El precio negociado ya está cubierto por la copia del precio (punto 4), pero los límites no pueden salir solo del código si son distintos para cada cliente. Por eso la suscripción admite excepciones:

```
SUBSCRIPTION {
  int max_locations_override   -- nulo = lo que diga el plan
  int max_staff_override       -- nulo = lo que diga el plan
}
```

Nulo es el caso normal y significa "manda el plan". Con valor, manda la excepción. Así el plan Empresarial no necesita código aparte: es un plan más, con los topes puestos a mano por un `SUPER_ADMIN` tras la conversación.

### 4. El catálogo de planes vive en código; el precio pactado, en la suscripción

Los planes, con sus límites y su precio de lista, se definen en código, igual que las paletas de tema de la página pública.

Pero el precio que se le cobra a una barbería **se copia a su suscripción** cuando se contrata:

```
SUBSCRIPTION {
  varchar plan            -- TRIAL | STARTER | BASIC | PRO | ENTERPRISE
  int     base_amount     -- lo que se pactó, en pesos
}
```

Es el mismo principio del [ADR-010](./010-snapshot-precio-duracion.md): el precio de hoy no debe reescribir el acuerdo vigente. Si mañana el Básico sube a 60.000, quien se suscribió a 50.000 sigue en 50.000 hasta que se le avise y se le cambie explícitamente. Sin la copia, subir una constante en el código le subiría el cobro a todos los clientes actuales de un despliegue para otro.

Y es lo que permite que el plan Empresarial funcione sin ser un caso especial: su `base_amount` es sencillamente el número que se acordó.

### 5. Un solo descuento a la vez

```
SUBSCRIPTION {
  smallint discount_percent      -- 10 = diez por ciento
  smallint discount_months_left  -- cuántos cobros más lo llevan
  varchar  discount_reason       -- REFERRAL | PROMO | MANUAL
  uuid     promo_code_id         -- solo cuando el motivo es PROMO
}
```

Los campos del descuento van juntos: o todos nulos, o todos con valor. Una restricción `CHECK` lo sostiene, para que no quede un porcentaje sin vencimiento cobrando descuento para siempre.

**No se acumulan.** La estructura ya lo impide —hay un solo juego de campos, no una lista— y esa es justamente la razón de modelarlo así: dos descuentos sumados sobre un plan de 25.000 pesos pueden dejar el cobro en nada sin que nadie lo haya decidido.

Cuando alguien llega con código de referido **y** con código de promoción, manda **el de referido**. Es una regla simple y predecible, y hay una persona del otro lado a la que se le prometió una comisión por esa barbería; la campaña, en cambio, es impersonal. La interfaz debe decir cuál quedó aplicado, para que nadie crea que perdió su promoción en silencio.

> **Ojo con no confundir dos cosas.** Que el descuento de referido no se aplique **no** anula la relación de referido. `BUSINESS.referred_by_id` se guarda siempre que venga un código válido, y de ahí sale la comisión del referidor (Etapa 8). Lo que es excluyente es el descuento, no el vínculo.

Al generar el cobro mensual:

1. `monto = base_amount − (base_amount × discount_percent / 100)`
2. Se resta uno a `discount_months_left`.
3. Al llegar a cero, los campos del descuento se vacían y el siguiente cobro sale al precio normal.

**El descuento se apaga solo.** Nadie tiene que acordarse de quitarlo, que es exactamente el error que deja a un cliente con 10% de por vida.

### 6. Los códigos de promoción los crea Guardao desde su panel

Hay dos clases de código, y se parecen lo suficiente como para confundirlas:

| | **Código de referido** | **Código de promoción** |
|---|---|---|
| Quién lo crea | El sistema, uno por barbería al registrarse | Guardao, desde el panel interno |
| Dónde vive | `BUSINESS.referral_code` (ya existe) | Tabla `PROMO_CODE` |
| Caduca | No | Sí |
| Tope de usos | No | Opcional |
| Para qué | Que una barbería traiga a otra | Campañas: lanzamiento, temporada baja |

```
PROMO_CODE {
  uuid     id PK
  varchar  code UNIQUE        -- LANZAMIENTO10
  smallint discount_percent
  smallint discount_months    -- cuántos cobros dura
  timestamptz valid_until     -- hasta cuándo se puede usar
  int      max_uses           -- nulo = sin tope
  int      used_count
  boolean  is_active
}
```

Los crea y desactiva un `SUPER_ADMIN` desde el panel interno (Etapa 9). No se editan una vez usados: si hay que cambiar las condiciones, se desactiva el código y se crea otro, para que nadie descubra que la campaña que aceptó cambió después.

**Un código de promoción no puede repetir un código de referido.** Se valida al crearlo. Sin esa validación, un código ambiguo haría que la misma cadena de texto significara dos cosas distintas según qué tabla se consulte primero.

En el formulario de registro es **una sola casilla**: "¿tienes un código?". Quien se registra no tiene por qué saber de qué tipo es el suyo; el backend lo resuelve buscando en ambos lados.

### 7. Qué pasa cuando la prueba termina o la suscripción se vence

La cuenta pasa a **solo lectura** y la página pública deja de aceptar reservas nuevas. Pero:

- Las citas ya agendadas siguen visibles para la barbería.
- Los enlaces de gestión que ya recibieron los clientes siguen funcionando para ver, cancelar o reprogramar.

Apagar la barbería por completo castigaría a sus clientes finales, que no tienen nada que ver con que el pago no entró. Y una cuenta que borra la agenda al vencer es una cuenta que nadie recupera.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Tabla de planes en la base de datos** | Cambiar un precio no debería ser una migración de datos. Y el plan no es solo una cifra: son límites que el código tiene que hacer cumplir de todos modos, así que la mitad quedaría en código igual. Se prefiere una sola fuente, con las excepciones del punto 3 para lo negociado |
| **Precio solo en código, sin copia en la suscripción** | Subir la constante le cambiaría el cobro a todos los suscriptores actuales en el siguiente despliegue. Es el problema del [ADR-010](./010-snapshot-precio-duracion.md), aplicado al propio negocio de Guardao |
| **Permitir descuentos acumulables** | Sobre un plan de 25.000 pesos, dos promociones sumadas dejan el cobro en casi nada sin que nadie lo haya decidido. Y vuelve imposible explicarle a un cliente por qué pagó lo que pagó |
| **Que gane el descuento más alto cuando vengan los dos** | Más amable, pero impredecible: el mismo código da resultados distintos según con qué se combine, y hay que comparar porcentajes contra duraciones (¿20% un mes o 10% tres meses?). Si se prefiere esta regla, es cambiar una condición |
| **Tabla de descuentos con historial** | El historial de lo cobrado ya está en `PAYMENT`. Se reconsidera si algún día hay que acumular varios descuentos a la vez, que hoy se decidió no permitir |
| **Limitar el número de reservas al mes** | Castiga a la barbería justo cuando le va bien, y la empuja a no registrar citas para no pasarse del cupo. Un límite que corrompe los datos que la plataforma necesita es peor que no tener límite |
| **Cobrar por barbero (precio por asiento)** | Más justo en teoría, pero convierte cada contratación en una conversación de precio y desincentiva registrar al personal. Con planes fijos, el dueño sabe qué paga |

## Consecuencias

**Ganamos**

- Cambiar un precio es una línea de código, y no toca a quien ya está suscrito
- El plan Empresarial no necesita código especial: es precio y topes puestos a mano
- El descuento vence solo, sin depender de que alguien lo recuerde
- Guardao puede lanzar una campaña sin desplegar: crear un código es una operación del panel
- Lo que ve el cliente final es igual en todos los planes

**Aceptamos**

- **Los límites hay que hacerlos cumplir en el código.** Crear una sede o un barbero tiene que consultar el plan y sus excepciones; un endpoint que se olvide regala el plan Profesional. Es el mismo riesgo del [ADR-004](./004-aislamiento-multi-tenant.md) y merece el mismo trato: tests que intenten pasarse del límite
- **Cinco planes son cinco caminos que probar.** Cada límite nuevo multiplica los casos: conviene que los topes vivan en un solo lugar consultable y no repartidos por los servicios
- **El cupo de WhatsApp exige contar mensajes.** La entidad `NOTIFICATION` ya existe y guarda cada envío, así que el conteo sale de ahí; falta decidir qué ocurre al agotarlo (¿cae a correo, o se avisa al dueño?)
- **Un referido cuesta dos veces.** Si el referido entra con 10% de descuento y además el referidor se lleva el 10% de esos pagos (Etapa 8), el margen de esa barbería baja cerca del 20% durante los meses de promoción. Puede estar bien como costo de adquisición, pero es una decisión de negocio, no un efecto que deba descubrirse leyendo un informe

## Pendiente de definir

Esto no bloquea el modelo, pero hay que cerrarlo antes de la Etapa 5:

- El número exacto de los cupos de WhatsApp por plan, que depende del precio por mensaje de Meta en Colombia
- Si el descuento por referido y la comisión al referidor conviven o si uno reemplaza al otro
- El tope de sedes del plan Profesional: hoy son 5, puesto como punto de partida
- Qué pasa con una barbería que baja de plan y queda por encima del límite nuevo (¿se bloquea crear más, o hay que desactivar sedes?)
