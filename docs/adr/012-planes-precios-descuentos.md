# ADR-012 — Planes de suscripción, precios y descuentos

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

Guardao cobra una suscripción mensual a cada barbería. El esquema inicial ya tiene la tabla `SUBSCRIPTION`, pero solo guarda `plan` (un texto), `status` y hasta cuándo está pagado el período. **No guarda cuánto cuesta ni qué habilita.**

Hay cinco cosas por decidir antes de escribir el cobro recurrente (Etapa 5):

1. Cuáles son los planes y qué incluye cada uno.
2. Dónde vive el precio, sabiendo que "50.000 pesos" es el precio de **hoy** y va a subir.
3. Cómo se representan las ofertas de bienvenida.
4. De dónde salen los códigos de promoción y quién los crea.
5. Cómo se le paga a quien refiere a otra barbería.

## Decisión

### 1. Cinco planes

| | **Prueba** | **Inicial** | **Básico** | **Profesional** | **Empresarial** |
|---|---|---|---|---|---|
| Precio | Gratis, 15 días | 25.000 COP | 50.000 COP | 100.000 COP | Desde 120.000, a convenir |
| Sedes | 1 | 1 | 1 | hasta 3 | sin límite |
| Barberos | hasta 6 | 1 | hasta 6 | sin límite | sin límite |
| Agenda y reservas | ✅ | ✅ | ✅ | ✅ | ✅ |
| Página pública de reservas | ✅ | ✅ | ✅ | ✅ | ✅ |
| Confirmar, cancelar y reprogramar por enlace | ✅ | ✅ | ✅ | ✅ | ✅ |
| Cobros con Wompi y adelantos | ✅ | ✅ | ✅ | ✅ | ✅ |
| Notificaciones por correo | ✅ | ✅ | ✅ | ✅ | ✅ |
| Notificaciones por WhatsApp | cupo medio | cupo bajo | cupo medio | cupo alto | volumen acordado |
| Programa de lealtad | ✅ | ✅ | ✅ | ✅ | ✅ |
| Catálogo de productos y carrito | ✅ | — | ✅ | ✅ | ✅ |
| Galería de Instagram y TikTok | ✅ | — | ✅ | ✅ | ✅ |
| Informes | completos | básicos | completos | completos | completos |

**La prueba es el plan Básico durante 15 días.** No el más caro: se prueba lo que la mayoría va a contratar. Enseñar funciones que el plan elegido no va a tener convierte la compra en una decepción.

**Inicial** es para el barbero que trabaja solo: un barbero, una sede, la agenda y la página de reservas completas. **Básico** es la barbería con equipo. **Profesional** es quien ya creció a varias sedes. **Empresarial** no se contrata desde la aplicación.

### 2. Qué se limita y qué no

**Nunca se limita lo que vive el cliente final de la barbería.** Reservar, confirmar, cancelar, reprogramar y recibir la confirmación funcionan igual en todos los planes. Quien reserva un corte no es cliente de Guardao y no tiene por qué notar en qué plan está la barbería.

Lo que separa los planes son dos cosas:

- **El costo variable real.** WhatsApp se envía desde **una sola cuenta de Guardao** compartida por todas las barberías (ver Notificaciones en el plan). Cada mensaje lo paga Guardao. Es lo único que crece con el uso, así que es lo único que necesita un tope: sin él, una barbería con 800 citas al mes puede costar más de lo que paga.
- **El tamaño del negocio.** Sedes y barberos. Quien tiene tres sedes factura más y puede pagar más.

Las líneas adicionales —lealtad, catálogo y galería— quedan disponibles desde el Básico. Solo el Inicial las deja fuera, porque un barbero que trabaja solo rara vez las usa y son lo que justifica el salto de 25.000 a 50.000.

En el Empresarial, "sin límite" en WhatsApp significa **sin tope técnico, con un volumen estimado dentro del precio acordado**. No es una promesa abierta: es el único costo que escala, y una cadena de diez sedes puede mandar miles de mensajes al mes.

### 3. El plan Empresarial no se contrata solo

Desde 120.000 pesos, pero el precio y los límites **se acuerdan hablando**. No hay botón que lo contrate: quien tiene ocho sedes necesita una conversación, no un formulario.

El precio negociado ya está cubierto por la copia del precio (punto 4). Para los límites, la suscripción admite excepciones:

```
SUBSCRIPTION {
  int max_locations_override   -- nulo = lo que diga el plan
  int max_staff_override       -- nulo = lo que diga el plan
}
```

Nulo es el caso normal y significa "manda el plan". Con valor, manda la excepción. Así el Empresarial no necesita código aparte: es un plan más, con los topes puestos a mano por un `SUPER_ADMIN` tras la conversación.

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

Y es lo que permite que el Empresarial funcione sin ser un caso especial: su `base_amount` es sencillamente el número que se acordó.

### 5. El descuento de bienvenida: uno solo

```
SUBSCRIPTION {
  smallint discount_percent      -- 10 = diez por ciento
  smallint discount_months_left  -- cuántos cobros más lo llevan
  varchar  discount_reason       -- REFERRAL | PROMO | MANUAL
  uuid     promo_code_id         -- solo cuando el motivo es PROMO
}
```

Los campos van juntos: o todos nulos, o todos con valor. Una restricción `CHECK` lo sostiene, para que no quede un porcentaje sin vencimiento cobrando descuento para siempre.

**No se acumulan.** La estructura ya lo impide —hay un solo juego de campos, no una lista— y esa es justamente la razón de modelarlo así: dos descuentos sumados sobre un plan de 25.000 pesos pueden dejar el cobro en nada sin que nadie lo haya decidido.

Cuando alguien llega con código de referido **y** con código de promoción, manda **el de referido**: hay una persona del otro lado a la que ese registro le genera un beneficio, mientras que la campaña es impersonal. La interfaz debe decir cuál quedó aplicado.

> **Ojo con no confundir dos cosas.** Que no se aplique el descuento de referido **no** anula la relación de referido. `BUSINESS.referred_by_id` se guarda siempre que venga un código válido, y de ahí sale el beneficio del referidor (punto 6). Lo excluyente es el descuento de bienvenida, no el vínculo.

### 6. Al referidor se le paga descontándole su propia factura

Guardao **nunca le transfiere dinero a nadie**. Pagar comisiones salientes exige datos bancarios, conciliación, retenciones y una operación mensual que a estos precios cuesta más que la comisión. En su lugar, el beneficio del referidor es un descuento en lo que él mismo paga.

La regla:

- Cada barbería referida que **esté pagando** descuenta **10 puntos porcentuales** de la suscripción de quien la refirió.
- El beneficio dura los **primeros 3 pagos** de la referida. Después se apaga.
- Los descuentos de varios referidos **se suman**: tres referidos pagando son 30% menos.
- Al llegar a **100% el mes sale gratis**. Lo que pase de ahí **se pierde**: no queda saldo a favor, no se acumula para el mes siguiente, no se paga en efectivo.
- **Cada mes se recalcula desde cero.** El mes siguiente se vuelve a mirar quién está pagando y cuántos pagos lleva.

Ejemplo, con el referidor en Básico (50.000):

| | Referidos que pagaron | Descuento | Paga |
|---|---|---|---|
| Mes 1 | 3 | 30% | 35.000 |
| Mes 2 | 3 | 30% | 35.000 |
| Mes 3 | 2 (uno no pagó) | 20% | 40.000 |
| Mes 4 | 0 (los tres cumplieron sus 3 pagos) | — | 50.000 |

**El 10% es de la factura del referidor, no de la del referido.** Así "trae diez barberías y no pagas" es cierto siempre, sin importar en qué plan estén ellas. Calcularlo sobre lo que paga el referido haría que el mismo esfuerzo valiera cuatro veces más o menos según el plan del otro, y volvería la promesa imposible de enunciar en una frase.

**Este beneficio reemplaza el monto fijo** que se había previsto por cada barbería referida. Un solo mecanismo, y de los dos se conserva el que no requiere mover dinero.

Dos plazos, ambos de 3 días y fáciles de confundir:

| Situación | Regla |
|---|---|
| La referida se atrasa en su pago | Tiene **3 días hábiles** de gracia. Si paga dentro de ellos, el referidor conserva el descuento de ese mes; si no, lo pierde. La gracia existe porque no todos activan el cobro automático |
| La referida cancela y vuelve | Si vuelve **antes de 3 días**, sigue contando para el referidor. Pasados los 3 días, el vínculo deja de generar beneficio |

**Esto no cabe en los campos del punto 5, y es correcto que no quepa.** El descuento de bienvenida es uno solo y con vencimiento fijo; el del referidor es acumulable y cambia todos los meses. Son dos cosas distintas y se calculan distinto: el del referidor **no se guarda**, se calcula al facturar contando las barberías con `referred_by_id` apuntando a esta que lleven 3 pagos o menos. Sale de datos que ya existen, sin tabla nueva.

### 7. Los códigos de promoción los crea Guardao desde su panel

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

**Un código de promoción no puede repetir un código de referido.** Se valida al crearlo. Sin esa validación, la misma cadena de texto significaría dos cosas distintas según qué tabla se consulte primero.

En el formulario de registro es **una sola casilla**: "¿tienes un código?". Quien se registra no tiene por qué saber de qué tipo es el suyo; el backend lo resuelve buscando en ambos lados.

### 8. Subir y bajar de plan

**Subir** aplica de inmediato: el plan nuevo siempre permite más, así que no hay nada que validar. El precio nuevo se cobra en el siguiente ciclo y Guardao regala los días que quedan del mes. Prorratear la diferencia significaría un cobro extra contra Wompi a mitad de mes para recuperar unos 25.000 pesos: la complejidad no se paga sola. Al subir, `base_amount` se actualiza al precio de lista vigente ese día.

**Bajar** se pide, pero **solo se ejecuta cuando la barbería cabe en el plan destino**. Si sobra algo, la aplicación muestra exactamente qué y lo va marcando a medida que se resuelve:

```
Para pasar a Básico te falta:
  ☑ Dejar 1 sede activa          — hecho, cerraste Sede Norte
  ☐ Dejar máximo 6 barberos      — te sobran 2

El cambio se aplica el 12 de septiembre, al terminar tu período pagado.
```

**Nada se desactiva solo.** El sistema no elige qué sede cerrar: esa decisión la toma quien conoce el negocio, una por una. Desactivar automáticamente podría cerrar una sede con citas ya agendadas, y quien lo pagaría serían clientes finales que no tienen nada que ver.

El cambio se ejecuta **al final del período ya pagado**, no de inmediato: nadie pierde días que pagó, y no hay devoluciones que gestionar. Al ejecutarse se vuelve a validar, por si en el intervalo se crearon sedes nuevas.

**Bajar nunca borra datos.** Las tarjetas de lealtad, los productos y las fotos de la galería quedan guardados; dejan de mostrarse y reaparecen si vuelve a subir. Los pedidos ya hechos siguen en el historial.

### 9. Qué pasa cuando la prueba termina o la suscripción se vence

La cuenta pasa a **solo lectura** y la página pública deja de aceptar reservas nuevas. Pero:

- Las citas ya agendadas siguen visibles para la barbería.
- Los enlaces de gestión que ya recibieron los clientes siguen funcionando para confirmar, ver, cancelar o reprogramar.

Apagar la barbería por completo castigaría a sus clientes finales, que no tienen nada que ver con que el pago no entró. Y una cuenta que borra la agenda al vencer es una cuenta que nadie recupera.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **La prueba con el plan más caro** | Enseñar catálogo, galería y sedes múltiples a quien va a contratar el Inicial convierte la compra en una decepción, y obliga a bajar de plan justo al sacar la tarjeta |
| **Pagarle al referidor en dinero** | Exige datos bancarios, conciliación y una operación de pagos salientes cada mes. A estos precios, mover 5.000 pesos cuesta más que los 5.000 |
| **Que el referidor gane el 10% de lo que paga el referido** | El mismo esfuerzo valdría cuatro veces más o menos según el plan del otro, y "trae diez y no pagas" dejaría de ser cierto. La promesa tiene que caber en una frase |
| **Acumular el excedente sobre el 100%** | Obliga a llevar un saldo a favor, que es contabilidad nueva para un caso raro. Se prefiere que cada mes se resuelva solo |
| **Tabla de planes en la base de datos** | Cambiar un precio no debería ser una migración de datos. Y el plan no es solo una cifra: son límites que el código tiene que hacer cumplir igual |
| **Precio solo en código, sin copia en la suscripción** | Subir la constante le cambiaría el cobro a todos los suscriptores actuales en el siguiente despliegue. Es el problema del [ADR-010](./010-snapshot-precio-duracion.md), aplicado al propio negocio |
| **Permitir descuentos de bienvenida acumulables** | Sobre un plan de 25.000, dos promociones sumadas dejan el cobro en casi nada sin que nadie lo decidiera, y vuelve imposible explicarle a un cliente por qué pagó lo que pagó |
| **Desactivar automáticamente lo que sobra al bajar de plan** | El sistema cerraría sedes con citas agendadas. Los clientes finales pagarían una decisión administrativa ajena |
| **Limitar el número de reservas al mes** | Castiga a la barbería justo cuando le va bien, y la empuja a no registrar citas. Un límite que corrompe los datos que la plataforma necesita es peor que no tener límite |

## Consecuencias

**Ganamos**

- Cambiar un precio es una línea de código, y no toca a quien ya está suscrito
- El Empresarial no necesita código especial: es precio y topes puestos a mano
- El descuento de bienvenida vence solo, sin depender de que alguien lo recuerde
- El beneficio del referidor no se guarda ni se concilia: se calcula al facturar, con datos que ya existen
- Guardao no mueve dinero hacia afuera en ningún caso
- Lo que ve el cliente final es igual en todos los planes

**Aceptamos**

- **Los límites hay que hacerlos cumplir en el código.** Crear una sede o un barbero tiene que consultar el plan y sus excepciones; un endpoint que se olvide regala el plan Profesional. Es el mismo riesgo del [ADR-004](./004-aislamiento-multi-tenant.md) y merece el mismo trato: tests que intenten pasarse del límite
- **La factura del referidor cambia todos los meses.** Es correcto, pero hay que poder explicarla: conviene que el recibo detalle cuántos referidos contaron y por cuánto, o el dueño va a creer que le cobraron mal
- **El cupo de WhatsApp exige contar mensajes.** La entidad `NOTIFICATION` ya existe y guarda cada envío, así que el conteo sale de ahí; falta decidir qué ocurre al agotarlo (¿cae a correo, o se avisa al dueño?)
- **Bajar de plan tiene fricción deliberada.** Quien quiera irse a un plan menor tendrá que desactivar cosas primero. Es preferible a que el sistema apague lo que no debe, pero hay que redactar esos mensajes con cuidado para que se lea como una guía y no como un castigo

## Pendiente de definir

- El número exacto de los cupos de WhatsApp por plan, que depende del precio por mensaje de Meta en Colombia
- Si la racha de la referida se rompe o se pausa cuando deja de pagar más allá de los 3 días hábiles de gracia
