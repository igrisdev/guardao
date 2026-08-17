# ADR-002 — Monolito modular sobre microservicios

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

El sistema tiene partes con responsabilidades distintas: reservas, pagos, notificaciones, catálogo, administración interna. La pregunta obligada es si se despliegan juntas o por separado.

## Decisión

**Un solo backend desplegable**, organizado en módulos por paquete, con fronteras explícitas entre ellos.

```
com.guardao.backend
├── auth/           Autenticación, JWT, resolución de tenant
├── business/       Negocios, sedes, usuarios
├── staff/          Barberos, servicios, habilidades
├── schedule/       Horarios, bloqueos, motor de disponibilidad
├── booking/        Citas, clientes, estados
├── payment/        Adaptador de pasarela, webhooks, suscripciones
├── notification/   WhatsApp, correo, plantillas
├── catalog/        Productos, pedidos, galería  (Etapa 7)
└── admin/          Panel interno de Guardao      (Etapa 9)
```

Regla: un módulo se comunica con otro **a través de sus servicios públicos**, nunca accediendo a sus repositorios directamente.

## Alternativas consideradas

### Microservicios desde el inicio

Descartada. Con 4 desarrolladores y cero usuarios, microservicios traen problemas nuevos sin resolver ninguno existente:

- Despliegues coordinados entre servicios
- Consistencia eventual donde hoy basta una transacción
- Trazas distribuidas para depurar un flujo simple
- Cuatro veces la infraestructura para el mismo tráfico

El punto crítico: **crear una cita revalida disponibilidad e inserta en la misma transacción.** Si "disponibilidad" y "citas" fueran servicios separados, esa garantía transaccional desaparece y hay que reemplazarla por sagas o bloqueos distribuidos. Se estaría cambiando una solución simple y correcta por una compleja y frágil.

### Serverless

Descartada. Los arranques en frío perjudican la latencia de la página pública, y los jobs programados (recordatorios, cobros de suscripción) encajan mal en el modelo.

### Monolito sin módulos

Descartada. Cuesta lo mismo poner fronteras desde el principio, y sin ellas el código se enreda hasta que separarlo se vuelve imposible.

## Consecuencias

**Ganamos**
- Un despliegue, un log, una base de datos, una transacción
- Depuración directa: el flujo completo se sigue en un solo proceso
- Costo de infraestructura mínimo
- El equipo trabaja en paralelo por módulo sin coordinar despliegues

**Aceptamos**
- Todo escala junto. Si la página pública recibe mucho tráfico, escala también el panel de administración
- Un error grave puede tumbar todo el backend. Se mitiga con manejo de errores por módulo y monitoreo
- Las fronteras entre módulos **hay que sostenerlas en revisión de código**: nada impide técnicamente que alguien importe el repositorio de otro módulo. Es un acuerdo de equipo, no una barrera del compilador

**Cuándo revisar esta decisión.** Si un módulo específico (probablemente notificaciones o el motor de disponibilidad) se vuelve cuello de botella medible, se extrae ese solo. No antes, y no todos.
