# ADR-008 — Coolify como plataforma de despliegue

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

Se necesita desplegar frontend, backend y base de datos en dos entornos (staging y producción) sobre un VPS propio, con presupuesto bajo y sin dedicar una persona a infraestructura.

## Decisión

**Coolify** sobre un VPS de Hetzner o DigitalOcean con 8 GB de RAM.

Dos proyectos separados —`staging` y `producción`— cada uno con **su propia instancia de PostgreSQL**. Nunca comparten base de datos.

Despliegue automático: `develop` → staging, `main` → producción.

## Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| **Coolify** | Gestión de equipos y permisos, entornos múltiples, comunidad grande (55k+ estrellas) | ~1.2 GB de RAM en reposo |
| Dokploy | Más liviano (~0.8 GB), instalación más simple | Comunidad menor (~24k); gestión de equipos menos desarrollada |
| Vercel + Railway/Neon | Cero mantenimiento | Costo creciente por servicio; el backend Java encaja mal en Vercel |
| Kubernetes | Escala sin límite | Complejidad desproporcionada para 4 personas y un VPS |
| Docker Compose a mano | Control total | Hay que construir despliegue continuo, SSL y respaldos uno mismo |

Lo decisivo frente a Dokploy fue la **gestión de equipos y entornos**: con 4 desarrolladores y cuatro servicios corriendo, importa más que el consumo de memoria. En un VPS de 8 GB, la diferencia de 400 MB no es un problema real.

## Consecuencias

**Ganamos**
- SSL automático, despliegue desde Git, variables de entorno por entorno y respaldos programados, sin construir nada
- Docker viene incluido: no hay que decidirlo aparte
- Costo fijo de 20–30 USD al mes, todo incluido

**Aceptamos**
- **Somos responsables del servidor**: actualizaciones del sistema, seguridad, disco. No hay proveedor que lo haga por nosotros
- Un solo VPS es un punto único de fallo. Staging y producción caen juntos si el servidor se cae. Aceptable en esta etapa; los respaldos diarios son la red de seguridad
- Coolify es un componente más que puede fallar o requerir actualización
- Si el proyecto crece mucho, habrá que separar producción a su propio servidor. No antes

## Decisión complementaria: Docker en local, solo para la base de datos

El `docker-compose.yml` del repositorio levanta **únicamente PostgreSQL**. Backend y frontend corren nativos en cada máquina, porque el recargado en caliente es notablemente más rápido así.

Esto garantiza que los 4 desarrolladores usen la misma versión y configuración de base de datos, evitando el clásico "a mí sí me funciona", sin sacrificar velocidad de desarrollo.
