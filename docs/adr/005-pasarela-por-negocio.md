# ADR-005 — Cada negocio conecta su propia pasarela de pagos

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

En el producto conviven dos flujos de dinero completamente distintos:

1. **El cliente final le paga a la barbería** por su corte (adelanto al reservar, o el servicio en la cita)
2. **La barbería le paga a Guardao** la suscripción mensual

La pregunta es si Guardao recibe el dinero del flujo 1 y lo transfiere, o si va directo al negocio.

## Decisión

**Cada barbería conecta su propia cuenta de Wompi.** El dinero de sus clientes le llega directo; Guardao no lo toca en ningún momento.

Las credenciales se guardan cifradas en reposo en la tabla `GATEWAY`, nunca en texto plano, nunca en logs, y ningún endpoint las devuelve.

El acceso a la pasarela se hace a través de una interfaz `PaymentGatewayAdapter` (crear transacción, verificar firma de webhook, consultar estado), con `WompiAdapter` como primera implementación. Ningún servicio de negocio importa clases de Wompi.

| Flujo | Origen | Destino | Credenciales usadas |
|---|---|---|---|
| Adelanto y servicios | Cliente final | Cuenta de la barbería | Las del negocio (`GATEWAY`) |
| Suscripción mensual | Barbería | Cuenta de Guardao | Las de Guardao (variables de entorno) |

## Alternativas consideradas

### Guardao recibe todo y transfiere después

Descartada, y no por complejidad técnica sino por **consecuencias regulatorias y de riesgo**:

- Recibir dinero de terceros para transferirlo convierte a Guardao en intermediario financiero, con las obligaciones que eso implica en Colombia
- Los contracargos de un cliente final terminarían siendo problema de Guardao
- Habría que construir un sistema de saldos, liquidaciones y pagos a negocios: un producto entero aparte
- Un problema en la cuenta de Guardao congelaría el dinero de **todas** las barberías

El beneficio (onboarding más simple) no compensa ninguno de esos costos.

### Solo efectivo, sin pasarela

Descartada. Elimina el adelanto, que es la palanca principal contra las inasistencias y una de las razones por las que una barbería pagaría por el producto.

### Integrar Wompi directamente, sin adaptador

Descartada. El plan contempla sumar PayU u otra pasarela más adelante. Sin la interfaz, sumar una segunda significa tocar todos los servicios que hablan de pagos. Con ella, es una clase nueva.

## Consecuencias

**Ganamos**
- Guardao nunca maneja dinero ajeno: sin obligaciones de intermediación, sin riesgo de contracargos, sin sistema de liquidaciones
- Cada barbería ve su plata en su cuenta, de inmediato. Es más fácil de vender y de confiar
- Sumar otra pasarela no exige rediseñar nada

**Aceptamos**
- **El onboarding es más largo**: la barbería debe abrir y conectar su cuenta Wompi antes de cobrar en línea. Se mitiga permitiendo operar sin pagos — la agenda funciona igual y el adelanto es opcional
- **Guardamos credenciales de terceros**, lo que nos vuelve un objetivo. El cifrado en reposo no es opcional, y hay un procedimiento de rotación en el [Runbook](../runbook-guardao.md)
- Depurar un pago requiere saber de qué negocio es, porque cada uno usa credenciales distintas
- Los webhooks deben resolver a qué negocio pertenece cada evento antes de procesarlo
