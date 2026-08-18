# Runbook — Guardao

Manual de operación en vivo. Qué hacer cuando algo falla, paso a paso.

> **Antes de tocar producción:** anota qué vas a hacer y por qué en el canal del equipo. Si el problema afecta a barberías en operación, avisa primero y arregla después — un dueño que sabe que estás trabajando en el problema es un dueño tranquilo.

---

## Índice de incidentes

| # | Síntoma | Gravedad |
|---|---|---|
| [1](#1--doble-reserva-en-produccion) | Dos citas cruzadas con el mismo barbero | 🔴 Crítico |
| [2](#2--un-negocio-ve-datos-de-otro) | Fuga de datos entre negocios | 🔴 Crítico |
| [3](#3--webhook-de-wompi-fallando) | Pagos aprobados que no se reflejan | 🟠 Alto |
| [4](#4--whatsapp-no-envía) | Clientes sin confirmación ni recordatorio | 🟠 Alto |
| [5](#5--token-de-whatsapp-expirado) | Todos los envíos fallan con 401 | 🟠 Alto |
| [6](#6--el-job-de-recordatorios-no-corre) | Nadie recibe recordatorios | 🟠 Alto |
| [7](#7--cobro-de-suscripción-fallido) | Suscripción de una barbería sin cobrar | 🟡 Medio |
| [8](#8--migración-de-flyway-fallida) | El backend no arranca | 🔴 Crítico |
| [9](#9--el-pipeline-de-ci-se-detiene) | No se puede mergear | 🟡 Medio |
| [10](#10--el-backend-no-responde) | 502 en toda la aplicación | 🔴 Crítico |
| [11](#11--credenciales-comprometidas) | Sospecha de filtración | 🔴 Crítico |
| [12](#12--restaurar-un-respaldo) | Pérdida de datos | 🔴 Crítico |

---

## 1 · Doble reserva en producción

**Síntoma:** una barbería reporta dos clientes citados a la misma hora con el mismo barbero.

**Esto no debería poder ocurrir.** La restricción `EXCLUDE` lo impide a nivel de motor ([ADR-003](./adr/003-exclude-constraint-doble-reserva.md)). Si ocurrió, la restricción no está.

### Diagnóstico

```sql
-- ¿Existe la restricción?
SELECT conname, contype FROM pg_constraint
WHERE conrelid = 'appointment'::regclass AND conname = 'appointment_no_overlap';

-- ¿Está instalada la extensión?
SELECT extname FROM pg_extension WHERE extname = 'btree_gist';

-- Buscar solapamientos existentes
SELECT a1.id, a2.id, a1.staff_id, a1.scheduled_at, a2.scheduled_at
FROM appointment a1
JOIN appointment a2
  ON a1.staff_id = a2.staff_id
 AND a1.id < a2.id
 AND a1.status IN ('PENDING','CONFIRMED')
 AND a2.status IN ('PENDING','CONFIRMED')
 AND tstzrange(a1.scheduled_at, a1.scheduled_at + (a1.duration_min||' minutes')::interval)
  && tstzrange(a2.scheduled_at, a2.scheduled_at + (a2.duration_min||' minutes')::interval);
```

### Acción

1. **Avisa a la barbería de inmediato** con los datos de ambas citas, para que llame a un cliente y lo reubique.
2. Si la restricción **no existe**, recréala. Va a fallar si hay solapamientos: resuélvelos primero manualmente (cancelando una de las citas en conflicto), luego crea la restricción.
3. Revisa el historial de migraciones: `SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;`
4. Abre un incidente y averigua **cómo desapareció**. Alguien la eliminó, o una migración nunca corrió en producción.

### Prevención

El test de concurrencia en CI debe estar activo. Si alguien lo marcó como ignorado, es un fallo de proceso, no de código.

---

## 2 · Un negocio ve datos de otro

**Síntoma:** un dueño reporta ver citas, clientes o barberos que no son suyos.

🔴 **Es un incidente de confidencialidad.** Tratar con la misma urgencia que una filtración de credenciales.

### Acción inmediata

1. **Identifica el endpoint** por el que se filtró (logs de la petición reportada).
2. **Evalúa el alcance**: ¿un endpoint puntual o algo transversal? Si es transversal, considera poner el servicio en mantenimiento.
3. **Corrige y despliega** con prioridad sobre cualquier otra cosa.
4. **Documenta qué datos se expusieron y a quién.** Bajo la Ley 1581 puede haber obligación de notificar a los titulares.

### Diagnóstico

```sql
-- ¿Hay filas huérfanas o mal asociadas?
SELECT c.id, c.business_id, c.phone FROM client c
LEFT JOIN business b ON b.id = c.business_id
WHERE b.id IS NULL;
```

Revisa el endpoint: ¿toma el `business_id` del JWT o lo acepta de la petición? Si es lo segundo, ese es el bug.

---

## 3 · Webhook de Wompi fallando

**Síntoma:** el cliente pagó, Wompi muestra la transacción aprobada, pero en Guardao sigue pendiente.

### Diagnóstico

```bash
# Logs del webhook en la última hora
docker logs guardao-backend --since 1h 2>&1 | grep -i "webhooks/wompi"
```

```sql
-- Pagos atascados
SELECT id, type, method, amount, status, created_at
FROM payment
WHERE status = 'PENDING' AND created_at < now() - interval '30 minutes'
ORDER BY created_at DESC;
```

### Causas y solución

| Causa | Cómo se ve | Solución |
|---|---|---|
| Firma inválida | `INVALID_WEBHOOK_SIGNATURE` en logs | Verifica que la llave configurada corresponda al entorno. **Error típico: llaves de sandbox en producción** |
| Wompi no alcanza el servidor | Sin registro del webhook | Verifica la URL configurada en el panel de Wompi y que el dominio resuelva |
| Excepción al procesar | Traza en los logs | Corrige y reprocesa manualmente |
| Credenciales del negocio erróneas | Falla solo para un negocio | Pídele al dueño que reconecte su cuenta |

### Reconciliación manual

Cuando el pago sí ocurrió pero no se reflejó, consulta el estado en Wompi con el identificador de transacción y actualiza:

```sql
UPDATE payment SET status = 'APPROVED', updated_at = now()
WHERE id = '<uuid-del-pago>';
-- Si corresponde a una cita, confirmarla también
UPDATE appointment SET status = 'CONFIRMED' WHERE id = '<uuid-de-la-cita>';
```

Deja registro de la intervención manual en el canal del equipo.

---

## 4 · WhatsApp no envía

**Síntoma:** los clientes no reciben confirmaciones ni recordatorios.

**Recuerda:** un fallo de notificación **no impide reservar**. Las citas se siguen creando. Es grave, pero no bloquea el negocio.

### Diagnóstico

```sql
-- Panorama de las últimas horas
SELECT channel, type, status, count(*)
FROM notification
WHERE sent_at > now() - interval '6 hours'
GROUP BY 1,2,3 ORDER BY 4 DESC;
```

### Causas y solución

| Causa | Señal | Solución |
|---|---|---|
| Token expirado | 401 de Meta | Ver [incidente 5](#5--token-de-whatsapp-expirado) |
| Plantilla rechazada o pausada | Error de plantilla | Revisar en WhatsApp Manager; reactivar o crear una nueva |
| Cuota agotada | 429 de Meta | Revisar límites de la cuenta; el correo cubre mientras tanto |
| Número mal formateado | Error por destinatario | Validar formato internacional (`+57...`) al guardar el cliente |
| Meta con incidente | Fallo generalizado | Esperar; el job reintenta solo |

### Mientras tanto

Verifica que el respaldo por correo esté funcionando:

```sql
SELECT status, count(*) FROM notification
WHERE channel = 'EMAIL' AND sent_at > now() - interval '1 hour'
GROUP BY 1;
```

---

## 5 · Token de WhatsApp expirado

**Síntoma:** todos los envíos fallan con 401. Ninguna barbería recibe notificaciones.

### Acción

1. Entra a Meta Business Manager → tu aplicación → WhatsApp → Configuración de la API
2. Genera un **token de acceso permanente** (no uno temporal de 24 horas)
3. Actualiza la variable de entorno en Coolify, entorno de producción
4. Reinicia el backend
5. Reintenta lo pendiente:

```sql
UPDATE notification SET status = 'PENDING'
WHERE status = 'FAILED'
  AND channel = 'WHATSAPP'
  AND sent_at > now() - interval '24 hours';
```

### Prevención

Los tokens permanentes no expiran solos, pero se invalidan si cambia la contraseña de la cuenta o los permisos de la aplicación. **Agenda una revisión trimestral.**

---

## 6 · El job de recordatorios no corre

**Síntoma:** nadie recibe recordatorios; las confirmaciones sí llegan.

### Diagnóstico

```sql
-- Citas próximas sin recordatorio enviado
SELECT a.id, a.scheduled_at
FROM appointment a
LEFT JOIN notification n
  ON n.appointment_id = a.id AND n.type = 'REMINDER'
WHERE a.scheduled_at BETWEEN now() AND now() + interval '24 hours'
  AND a.status IN ('PENDING','CONFIRMED')
  AND n.id IS NULL;
```

```bash
docker logs guardao-backend --since 2h 2>&1 | grep -i "reminder\|scheduled"
```

### Causas

| Causa | Solución |
|---|---|
| El backend se reinició y el job no reanudó | Reiniciar el servicio |
| Excepción sin capturar mató el hilo del job | Corregir; envolver el cuerpo del job en try/catch |
| Zona horaria mal configurada | Verificar que el servidor use UTC y las citas sean `timestamptz` |
| Varias instancias ejecutando el mismo job | Deuda técnica conocida: falta bloqueo distribuido |

Tras corregir, el job recupera solo las citas pendientes en su siguiente ciclo. **No dispares envíos manuales sin verificar** que no se dupliquen.

---

## 7 · Cobro de suscripción fallido

**Síntoma:** la suscripción de una barbería quedó marcada como fallida.

### Diagnóstico

```sql
SELECT s.id, b.name, s.plan, s.status, p.status AS ultimo_pago, p.created_at
FROM subscription s
JOIN business b ON b.id = s.business_id
LEFT JOIN payment p ON p.subscription_id = s.id
WHERE s.status IN ('PAST_DUE','FAILED')
ORDER BY p.created_at DESC;
```

### Acción

1. **Causa del rechazo** en el panel de Wompi: sin fondos, tarjeta vencida, tokenización inválida
2. **Avisa al dueño** por WhatsApp o correo, con instrucciones para actualizar su medio de pago
3. **Política de reintentos**: automáticos con retroceso. Tras agotarlos, la cuenta pasa a un estado degradado — decide con el equipo si se suspende el acceso o solo se muestra un aviso
4. **No suspendas sin avisar.** Una barbería que pierde su agenda sin previo aviso es una barbería que se va

---

## 8 · Migración de Flyway fallida

**Síntoma:** el backend no arranca. En los logs: error de validación o de migración.

### 8.1 Checksum alterado

```
Migration checksum mismatch for migration version X
```

**Causa:** alguien modificó un archivo de migración ya aplicado.

- **En local:** base limpia y listo.
  ```bash
  docker compose down -v && docker compose up -d
  ```
- **En staging o producción:** ⚠️ **nunca borres la base.** Revierte el archivo a su contenido original y crea una migración nueva con el cambio.

### 8.2 Migración aplicada a medias

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

Si la última tiene `success = false`:

1. **Respalda antes de tocar nada**
2. Revisa qué alcanzó a ejecutarse (Postgres es transaccional en DDL: normalmente revierte todo)
3. Elimina la fila fallida y corrige la migración:
   ```sql
   DELETE FROM flyway_schema_history WHERE success = false;
   ```
4. Vuelve a desplegar

### 8.3 Validación de Hibernate

```
Schema-validation: missing column [x] in table [y]
```

La entidad y la tabla no coinciden: falta la migración que agrega esa columna. **No cambies `ddl-auto` a `update` para salir del paso** — enmascara el problema y deja el esquema divergente entre entornos ([ADR-007](./adr/007-flyway-sobre-ddl-auto.md)).

---

## 9 · El pipeline de CI se detiene

**Síntoma:** los PR no se pueden mergear.

| Fallo | Diagnóstico | Solución |
|---|---|---|
| Test de concurrencia en rojo | Alguien tocó la restricción `EXCLUDE` o la lógica de reserva | **No lo ignores.** Es el test que protege el peor fallo del producto |
| Test de aislamiento en rojo | Un endpoint nuevo no filtra por `business_id` | Corregir el endpoint |
| Testcontainers no arranca | Sin Docker en el runner, o sin memoria | Revisar la configuración del runner |
| Flyway falla en CI | Migración inválida desde cero | Probar en local con base limpia |
| Build del frontend falla | Error de tipos o de dependencias | `pnpm install` y revisar el error |
| Timeout | Suite muy lenta | Paralelizar; revisar tests con esperas fijas |

> Si la urgencia empuja a saltarse el CI: **no**. Los tres tests que bloquean el merge son exactamente los que protegen contra los fallos irreversibles.

---

## 10 · El backend no responde

**Síntoma:** 502 en toda la aplicación.

### Diagnóstico rápido

```bash
docker ps -a | grep guardao          # ¿está corriendo?
docker logs guardao-backend --tail 200
df -h                                # ¿disco lleno?
free -m                              # ¿memoria?
docker exec guardao-postgres pg_isready -U guardao   # ¿la BD responde?
```

### Causas frecuentes

| Causa | Señal | Solución |
|---|---|---|
| Sin memoria | Contenedor reiniciado por OOM | Subir el límite o revisar fugas |
| Disco lleno | `No space left on device` | Limpiar logs e imágenes: `docker system prune -a` |
| Base de datos caída | `Connection refused` a 5432 | Reiniciar Postgres, revisar sus logs |
| Pool de conexiones agotado | Timeouts al obtener conexión | Buscar consultas lentas o transacciones sin cerrar |
| Fallo al arrancar | Ver [incidente 8](#8--migración-de-flyway-fallida) | — |

```sql
-- Consultas colgadas
SELECT pid, now() - query_start AS duracion, state, left(query, 100)
FROM pg_stat_activity
WHERE state <> 'idle' AND now() - query_start > interval '30 seconds'
ORDER BY duracion DESC;
```

---

## 11 · Credenciales comprometidas

**Síntoma:** secreto en un commit, acceso no autorizado, o sospecha fundada.

### Acción inmediata, en este orden

1. **Rota el secreto en el proveedor.** Borrar el commit no sirve: si estuvo publicado, asume que se copió.
2. **Actualiza la variable en Coolify** y reinicia.
3. **Evalúa el daño** según el secreto:

| Secreto | Consecuencia | Además |
|---|---|---|
| Secreto del JWT | Cualquiera falsifica sesiones | Rotar **invalida todas las sesiones**: los usuarios deben entrar de nuevo. Hazlo igual |
| Clave maestra de cifrado | Las credenciales Wompi guardadas quedan expuestas | Recifrar `GATEWAY`; considerar pedir a los negocios que reconecten |
| Wompi de Guardao | Riesgo financiero directo | Contactar a Wompi de inmediato |
| Token de WhatsApp | Envío de mensajes a nombre de Guardao | Revocar en Meta y generar uno nuevo |
| Credenciales de base de datos | Acceso total a los datos | Rotar, revisar accesos, considerar notificación bajo Habeas Data |

4. **Documenta el incidente**: qué se expuso, desde cuándo, y qué se hizo.

---

## 12 · Restaurar un respaldo

⚠️ **Última opción.** Restaurar significa perder todo lo ocurrido desde el respaldo.

### Antes de restaurar

1. **Respalda el estado actual**, por corrupto que parezca. Puede contener datos recuperables.
   ```bash
   docker exec guardao-postgres pg_dump -U guardao guardao > /tmp/antes-de-restaurar.sql
   ```
2. **Confirma que no hay alternativa.** ¿Se puede corregir con SQL puntual en vez de perder un día de citas?
3. **Avisa al equipo y a las barberías afectadas.**

### Restauración

```bash
# 1. Detener el backend para que nadie escriba
docker stop guardao-backend

# 2. Localizar el respaldo (Coolify → Backups)
ls -lh /ruta/de/backups/

# 3. Restaurar
docker exec -i guardao-postgres psql -U guardao -d guardao < backup-AAAA-MM-DD.sql

# 4. Verificar antes de abrir
docker exec guardao-postgres psql -U guardao -d guardao -c \
  "SELECT count(*) FROM appointment; SELECT max(created_at) FROM appointment;"

# 5. Levantar
docker start guardao-backend
```

### Después

- Verifica que la restricción `appointment_no_overlap` exista (ver [incidente 1](#1--doble-reserva-en-produccion))
- Revisa pagos en estado pendiente que ya se hayan aprobado en Wompi y reconcilia
- Avisa a las barberías qué rango de tiempo se perdió, para que reingresen citas si es necesario

### Simulacro trimestral

**Restaura el respaldo de producción en staging cada trimestre.** Un respaldo que nunca se restauró no es un respaldo: es un archivo del que suponemos algo. El simulacro también mide cuánto tarda de verdad la recuperación.

---

## Procedimiento · Crear un super-admin de Guardao

Los super-admin son el personal interno de Guardao. **No existe ninguna ruta HTTP que los cree**, ni siquiera protegida: un endpoint capaz de fabricar cuentas con acceso a toda la plataforma sería un blanco permanente, y no hace falta, porque estas cuentas se crean una vez por entorno.

Tampoco pertenecen a ninguna barbería. Guardao es la plataforma, no un negocio, así que su `business_id` va vacío — la base lo exige con la restricción `app_user_business_only_for_tenant_roles`.

### Cómo se crea

El backend lo crea al arrancar si encuentra estas dos variables de entorno. En Coolify se ponen en el entorno correspondiente:

```
GUARDAO_SUPERADMIN_EMAIL=nombre@guardao.co
GUARDAO_SUPERADMIN_PASSWORD=<clave de al menos 12 caracteres>
```

> **Ojo con el nombre.** La propiedad es `guardao.super-admin.email`, pero la variable va sin guion: Spring convierte los puntos en guion bajo y **elimina** los guiones. Escribirla como `GUARDAO_SUPER_ADMIN_EMAIL` no falla ni avisa — simplemente no se lee y el seed no hace nada. La misma regla aplica a cualquier otra propiedad con guion (`guardao.cors.allowed-origins` → `GUARDAO_CORS_ALLOWEDORIGINS`).

Reinicia el servicio. En el log verás:

```
Super-admin creado: nombre@guardao.co
```

Verifica que puede entrar con `POST /api/v1/auth/login` antes de seguir.

### Después de crearlo

1. **Quita las dos variables del entorno.** Dejarlas es guardar una contraseña en claro en la configuración de Coolify, a la vista de cualquiera con acceso al panel.
2. **Cambia la contraseña en el primer inicio de sesión.**

### Detalles que conviene saber

- **Es idempotente.** Si el correo ya existe, el arranque no lo toca: no te devuelve la contraseña original si ya la cambiaste. Para crear otro super-admin, usa un correo distinto.
- **Si la clave tiene menos de 12 caracteres, el arranque falla.** Es a propósito: mejor un despliegue detenido que una cuenta con acceso a todo mal protegida.
- **Nunca pongas estas variables en un archivo del repositorio.** Quedaría en el historial de git, de donde no se borra de verdad.
- **Un super-admin no tiene negocio, así que no puede usar los endpoints de una barbería** (`/api/v1/locations` y demás responden 403). El panel interno tiene los suyos, y llegan en la Etapa 9.

### Para dar de baja a alguien del equipo

No se borra la fila: se desactiva, porque sus acciones quedan en el historial.

```sql
UPDATE app_user SET is_active = false WHERE email = 'nombre@guardao.co';
```

El login lo rechaza de inmediato, y el refresco de sesión también, porque relee la cuenta en cada renovación.

---

## Contactos y accesos

| Recurso | Dónde |
|---|---|
| Panel de Coolify | *(URL del VPS)* |
| Panel de Wompi | https://comercios.wompi.co |
| Meta Business Manager | https://business.facebook.com |
| Repositorio | https://github.com/igrisdev/guardao |
| Tablero | https://johanalvarez.atlassian.net → proyecto GUA |

## Checklist post-incidente

- [ ] El servicio está operativo y verificado
- [ ] Las barberías afectadas fueron informadas
- [ ] Se creó el ticket en Jira con la causa raíz
- [ ] Se agregó el test que habría detectado esto
- [ ] Este runbook se actualizó si el procedimiento cambió
