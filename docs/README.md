# Documentación técnica — Guardao

## Por dónde empezar

| Si eres... | Lee en este orden |
|---|---|
| **Nuevo en el equipo** | Plan → PRD → Tech Spec |
| **Va a escribir código** | Tech Spec → ADR relevantes |
| **Va a operar producción** | Runbook → System Design |
| **Quiere entender una decisión** | El ADR correspondiente |

## Documentos

| Documento | Responde |
|---|---|
| [Plan del proyecto](./plan-proyecto-guardao.md) | Qué se construye y en qué orden. Fuente de verdad del alcance |
| [PRD](./prd-guardao.md) | Para quién es, qué funcionalidades tiene, cómo se mide el éxito |
| [RFC-001](./rfc-001-plataforma-reservas.md) | Qué problema resolvemos, qué alternativas descartamos, qué riesgos asumimos |
| [ADR](./adr/) | Por qué elegimos cada pieza del stack y cada decisión clave |
| [Tech Spec](./tech-spec-guardao.md) | Cómo se implementa: módulos, endpoints, esquema, flujos |
| [System Design](./system-design-guardao.md) | Cómo encaja todo, cómo escala, cómo se asegura |
| [Runbook](./runbook-guardao.md) | Qué hacer cuando algo falla en producción |
| [System Prompt Spec](./system-prompt-spec.md) | Reglas para los agentes de IA que usa el equipo |

## Las tres cosas que no se negocian

1. **La restricción `EXCLUDE` contra doble reserva** ([ADR-003](./adr/003-exclude-constraint-doble-reserva.md)). Dos clientes citados a la misma hora es el peor fallo del producto.
2. **El filtrado por `business_id`** ([ADR-004](./adr/004-aislamiento-multi-tenant.md)). Que una barbería vea datos de otra es una brecha de confidencialidad.
3. **Las migraciones de Flyway no se editan** ([ADR-007](./adr/007-flyway-sobre-ddl-auto.md)). Se corrigen con una migración nueva.

Los tres tienen un test que bloquea el merge si se rompen.

## Cómo mantener esto vivo

- El **plan** cambia cuando cambia el alcance
- Un **ADR** no se edita: se escribe uno nuevo que reemplace al anterior
- El **runbook** se actualiza después de cada incidente, mientras está fresco
- Si un documento y el código se contradicen, **gana el código** — y el documento se corrige el mismo día
