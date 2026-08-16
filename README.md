# Guardao

Plataforma de reservas con pagos integrados para negocios en Colombia. El desarrollo arranca con el **módulo de barberías**.

El plan completo del proyecto vive en [`docs/plan-proyecto-guardao.md`](docs/plan-proyecto-guardao.md). Es la única copia — no la dupliques dentro de `apps/backend/` ni `apps/frontend/`.

---

## Índice

1. [Estructura del repositorio](#1-estructura-del-repositorio)
2. [Stack y versiones](#2-stack-y-versiones)
3. [Requisitos previos](#3-requisitos-previos)
4. [Puesta en marcha paso a paso](#4-puesta-en-marcha-paso-a-paso)
5. [Comandos del día a día](#5-comandos-del-día-a-día)
6. [Flujo de trabajo con Git](#6-flujo-de-trabajo-con-git)
7. [Gestión de tareas en Jira](#7-gestión-de-tareas-en-jira)
8. [Problemas comunes](#8-problemas-comunes)

---

## 1. Estructura del repositorio

```
guardao/
├── apps/
│   ├── backend/          Spring Boot (API REST, Java 21)
│   └── frontend/         Next.js (App Router, React 19)
├── docs/
│   └── plan-proyecto-guardao.md
├── docker-compose.yml    PostgreSQL para desarrollo local
└── README.md
```

Solo la base de datos corre en Docker. El backend y el frontend corren nativos en tu máquina, para tener hot-reload rápido.

## 2. Stack y versiones

| Pieza | Versión | Nota |
|---|---|---|
| Java (JDK) | **21** | Obligatorio. Con 17 o 25 no compila igual. |
| Maven | 3.9.16 | No hace falta instalarlo: se usa el wrapper `mvnw` del repo. |
| Spring Boot | 4.1.0 | Definido en `apps/backend/pom.xml`. |
| Node.js | **22 LTS o superior** | |
| pnpm | **10.33.0** | Gestor de paquetes del frontend. No usar npm ni yarn. |
| Next.js | 16.3.1 | |
| PostgreSQL | 16 | Vía Docker (`docker-compose.yml`). |
| Docker Desktop | reciente | |

**Puertos que se usan:** `5432` (Postgres), `8080` (backend), `3000` (frontend).

## 3. Requisitos previos

Instala estas cuatro cosas antes de clonar. Elige la columna de tu sistema.

### Java 21

**macOS** (con [Homebrew](https://brew.sh)):

```bash
brew install openjdk@21
sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

Añade esto a tu `~/.zshrc` y abre una terminal nueva:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

**Windows** (PowerShell, con [winget](https://learn.microsoft.com/windows/package-manager/winget/)):

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK
```

Temurin configura `JAVA_HOME` solo. Cierra y abre PowerShell después de instalar. Si `java -version` no responde, configúralo a mano:

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot", "User")
```

(ajusta la ruta a la carpeta real que se creó en `C:\Program Files\Eclipse Adoptium\`)

**Verificar (ambos):**

```bash
java -version    # debe decir 21.x
echo $JAVA_HOME  # macOS/Linux
```
```powershell
echo $env:JAVA_HOME  # Windows
```

### Node.js 22 + pnpm

**macOS:**

```bash
brew install node@22
corepack enable
corepack prepare pnpm@10.33.0 --activate
```

**Windows:**

```powershell
winget install --id OpenJS.NodeJS.LTS
corepack enable
corepack prepare pnpm@10.33.0 --activate
```

> `corepack` viene incluido con Node y es la forma recomendada de fijar la versión exacta de pnpm. Si `corepack enable` da error de permisos en Windows, abre PowerShell **como administrador**.

**Verificar (ambos):**

```bash
node -v   # v22.x o superior
pnpm -v   # 10.33.0
```

### Docker Desktop

**macOS:**

```bash
brew install --cask docker
```

Ábrelo desde Aplicaciones la primera vez y espera a que el ícono de la ballena quede fijo en la barra de menú.

**Windows:**

```powershell
winget install --id Docker.DockerDesktop
```

Requiere **WSL2**. Si Docker Desktop se queja al arrancar, ejecuta en PowerShell como administrador:

```powershell
wsl --install
```

Reinicia el equipo, vuelve a abrir Docker Desktop y espera a que diga "Engine running".

**Verificar (ambos):**

```bash
docker --version
docker compose version
```

### Git

**macOS:** ya viene instalado (`git --version` lo confirma; si pide instalar las Command Line Tools, acepta).

**Windows:**

```powershell
winget install --id Git.Git
```

Configura tu identidad una sola vez:

```bash
git config --global user.name "Tu Nombre"
git config --global user.email "tu@correo.com"
```

**Windows, además** — evita que Git cambie los finales de línea y rompa el script `mvnw`:

```powershell
git config --global core.autocrlf input
```

## 4. Puesta en marcha paso a paso

### Paso 1 — Clonar el repositorio

```bash
git clone https://github.com/igrisdev/guardao.git
cd guardao
```

En Windows, clona en una ruta corta y sin espacios ni tildes (ej. `C:\dev\guardao`). Rutas largas dan problemas con `node_modules`.

### Paso 2 — Levantar PostgreSQL

Desde la raíz del repo, con Docker Desktop ya abierto:

```bash
docker compose up -d
```

Esto crea el contenedor `guardao-postgres` con estos datos (los mismos para todo el equipo, están en `docker-compose.yml`):

| Dato | Valor |
|---|---|
| Host | `localhost` |
| Puerto | `5432` |
| Base de datos | `guardao` |
| Usuario | `guardao` |
| Contraseña | `guardao` |

Comprueba que quedó arriba y sano:

```bash
docker compose ps
```

La columna `STATUS` debe decir `Up ... (healthy)`. Si dice `starting`, espera unos segundos y repite.

> Estas credenciales son **solo para local**. Las de staging y producción se configuran en Coolify y nunca viven en el repositorio.

### Paso 3 — Levantar el backend

```bash
cd apps/backend
```

**macOS / Linux:**

```bash
./mvnw spring-boot:run
```

Si te dice `permission denied`, dale permiso de ejecución una vez: `chmod +x mvnw`.

**Windows (PowerShell):**

```powershell
.\mvnw.cmd spring-boot:run
```

La primera vez tarda varios minutos: descarga Maven y todas las dependencias. Cuando esté listo verás algo como `Started BackendApplication in X seconds` y quedará escuchando en **http://localhost:8080**.

El perfil activo por defecto es `local` (`apps/backend/src/main/resources/application.yml`), que apunta al Postgres del Docker Compose y ejecuta las migraciones de Flyway al arrancar.

### Paso 4 — Levantar el frontend

En **otra terminal**, desde la raíz del repo:

```bash
cd apps/frontend
pnpm install
pnpm dev
```

Queda en **http://localhost:3000**. Ábrelo en el navegador para confirmar.

### Paso 5 — Confirmar que todo está arriba

| Servicio | URL / comando | Qué debes ver |
|---|---|---|
| PostgreSQL | `docker compose ps` | `Up (healthy)` |
| Backend | http://localhost:8080 | Responde (aún sin endpoints propios, un 404 de Spring ya confirma que está vivo) |
| Frontend | http://localhost:3000 | La página de Next.js |

Si los tres responden, tu entorno está listo.

## 5. Comandos del día a día

Todos desde la raíz del repo salvo que se indique otra cosa.

**Base de datos**

```bash
docker compose up -d      # levantar
docker compose stop       # apagar sin borrar datos
docker compose logs -f postgres   # ver logs
docker compose down -v    # BORRAR el contenedor y TODOS los datos (empezar de cero)
```

`down -v` elimina el volumen `pgdata`. Úsalo solo cuando quieras una base limpia — es la forma de resolver un conflicto de migraciones de Flyway en local.

Conectarte a la base por consola:

```bash
docker exec -it guardao-postgres psql -U guardao -d guardao
```

**Backend** (desde `apps/backend`)

| Acción | macOS / Linux | Windows |
|---|---|---|
| Arrancar | `./mvnw spring-boot:run` | `.\mvnw.cmd spring-boot:run` |
| Compilar | `./mvnw clean package` | `.\mvnw.cmd clean package` |
| Correr tests | `./mvnw test` | `.\mvnw.cmd test` |

**Frontend** (desde `apps/frontend`)

```bash
pnpm install     # tras cambiar de rama o si alguien tocó package.json
pnpm dev         # desarrollo con hot-reload
pnpm build       # build de producción
pnpm lint        # ESLint
```

## 6. Flujo de trabajo con Git

Repositorio: **https://github.com/igrisdev/guardao**

Ramas fijas (protegidas, nadie hace push directo a ellas):

- **`main`** — producción. Solo se toca por merge desde `develop`.
- **`develop`** — integración. Es la rama de la que sale y a la que vuelve todo el trabajo del equipo.

**Rama personal de cada desarrollador.** Además de las dos fijas, cada quien trabaja en su propia rama con el prefijo **`dev_`** seguido de su nombre y apellido en minúsculas, separados por guion bajo:

```
dev_johan_alvarez
dev_maria_ramirez
```

Sin tildes, sin espacios y sin mayúsculas. Cada quien usa siempre la misma rama, no una por tarea.

Crear tu rama personal la primera vez, a partir de `develop`:

```bash
git checkout develop
git pull origin develop

git checkout -b dev_johan_alvarez
git push -u origin dev_johan_alvarez
```

Ciclo de trabajo del día a día:

```bash
git checkout dev_johan_alvarez

# traes lo último del equipo a tu rama antes de empezar
git fetch origin
git merge origin/develop

# ... trabajas, commiteas ...
git add .
git commit -m "feat: descripción corta en presente"

git push origin dev_johan_alvarez
```

Luego abres un **Pull Request de tu rama `dev_` hacia `develop`** en GitHub. Reglas:

- Al menos un compañero revisa y aprueba antes del merge.
- Un PR con el build, los tests críticos o las migraciones en rojo **no se mergea**.
- Nunca hagas push directo a `develop` ni a `main`, ni a la rama `dev_` de otro compañero.
- Después de que te mergeen el PR, actualiza tu rama con `git pull origin develop` antes de seguir trabajando.

### Los tres merges que vas a usar

**1. Traer `develop` a tu rama personal** — hazlo seguido, idealmente cada mañana. Mientras menos tiempo pases desactualizado, menos conflictos tienes.

```bash
git checkout dev_johan_alvarez
git fetch origin
git merge origin/develop
git push origin dev_johan_alvarez
```

**2. Tu rama `dev_` → `develop`** — esto **no se hace a mano**. Se hace abriendo un Pull Request en GitHub y usando el botón *Merge pull request* después de la aprobación. Así queda la revisión registrada y corren los checks del CI.

Solo si necesitas verlo en local antes del PR (para probar cómo queda la integración), pero **sin pushear `develop`**:

```bash
git checkout develop
git pull origin develop
git merge dev_johan_alvarez     # revisas que compile y pasen los tests
git merge --abort               # o descartas: git reset --hard origin/develop
```

**3. `develop` → `main`** (lanzamiento a producción) — también por Pull Request en GitHub, y solo cuando lo validado en staging está listo. Lo hace quien coordine el release, no cada desarrollador.

### Resolver un conflicto de merge

Cuando `git merge origin/develop` te dice `CONFLICT`:

```bash
git status                  # lista los archivos en conflicto
# abres cada archivo y editas: borras los marcadores <<<<<<<, =======, >>>>>>>
# y dejas el código como debe quedar

git add archivo-que-arreglaste.java
git commit                  # sin -m: Git propone el mensaje del merge, lo aceptas
git push origin dev_johan_alvarez
```

Si te enredaste y quieres empezar de nuevo, mientras no hayas commiteado:

```bash
git merge --abort
```

Regla práctica: si el conflicto toca código de otro compañero, resuélvanlo entre los dos. No borres su trabajo para que compile.

---

Al mergear a `develop` se despliega automáticamente a **staging**; al mergear `develop` → `main`, a **producción**.

Prefijos de commit que usamos: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.

**Nunca commitees:** archivos `.env`, credenciales, llaves de Wompi, ni las carpetas `target/`, `node_modules/` o `.next/` (ya están en `.gitignore`).

## 7. Gestión de tareas en Jira

El trabajo se organiza en Jira: **https://johanalvarez.atlassian.net**

Cada etapa del roadmap es un **Epic**, y dentro van las tareas de backend, frontend y testing. Todo ticket tiene una clave del tipo `GUA-12` — esa clave es la que conecta Jira con el código.

### Ciclo de una tarea

1. Tomas un ticket del tablero y lo **asignas a ti mismo**.
2. Lo mueves a **En curso** antes de empezar a escribir código. Si no está en curso, para el equipo ese ticket sigue libre.
3. Trabajas en tu rama personal `dev_nombre_apellido` (ver [sección 6](#6-flujo-de-trabajo-con-git)).
4. Abres el PR hacia `develop`.
5. Cuando el PR se mergea, mueves el ticket a **Listo**.

### La clave del ticket va en el commit y en el PR

Escribe la clave **al inicio del mensaje de commit**, después del prefijo:

```bash
git commit -m "feat: GUA-12 agrega entidad Business y su repositorio"
git commit -m "fix: GUA-27 corrige validación de horario partido"
```

Y en el **título del Pull Request**:

```
GUA-12 Entidades Business, Location y User
```

Con eso Jira enlaza solo los commits y el PR al ticket, y cualquiera puede ver desde Jira qué código resolvió qué tarea. Un commit sin clave se pierde: nadie sabe a qué tarea pertenece.

Si un commit no corresponde a ningún ticket (ajustes menores, documentación), déjalo sin clave — pero que sea la excepción.

### Reglas del tablero

- **Un ticket en curso por persona.** Si te bloqueas, coméntalo en el ticket y toma otro; no dejes tres abiertos a la vez.
- **Comenta en el ticket, no en el chat.** Decisiones, dudas y bloqueos van en el ticket para que queden registrados donde está el trabajo.
- **Si algo no está en Jira, no existe.** Encontraste un bug o falta algo del plan: crea el ticket antes de ponerte a resolverlo.
- **No cambies el alcance de un ticket sobre la marcha.** Si crece, se parte en otro ticket.

## 8. Problemas comunes

**`port is already allocated` al levantar Docker (puerto 5432)**
Tienes un PostgreSQL instalado nativo ocupando el puerto. Apágalo:

```bash
brew services stop postgresql    # macOS
```
```powershell
Stop-Service postgresql*         # Windows, PowerShell como administrador
```

**El backend no arranca: `Connection refused` a `localhost:5432`**
El contenedor no está arriba o todavía está iniciando. Revisa con `docker compose ps` que diga `(healthy)` y vuelve a intentar.

**`invalid target release: 21` o el backend compila con otra versión de Java**
Tu `JAVA_HOME` apunta a otro JDK. Verifica con `java -version` y revisa el [paso de instalación de Java 21](#java-21). En Windows, recuerda cerrar y volver a abrir la terminal después de cambiar variables de entorno.

**`./mvnw: bad interpreter` o `permission denied` (macOS)**

```bash
chmod +x mvnw
```

Si el error menciona `^M`, el archivo llegó con finales de línea de Windows: configura `git config --global core.autocrlf input` y vuelve a clonar.

**`pnpm: command not found`**

```bash
corepack enable
corepack prepare pnpm@10.33.0 --activate
```

Cierra y abre la terminal.

**Errores raros del frontend tras cambiar de rama**

```bash
cd apps/frontend
rm -rf .next node_modules   # macOS/Linux
pnpm install
```
```powershell
Remove-Item -Recurse -Force .next, node_modules   # Windows
pnpm install
```

**Flyway falla con `checksum mismatch` o migración ya aplicada**
Alguien modificó una migración que ya habías corrido. En local se resuelve con base limpia:

```bash
docker compose down -v
docker compose up -d
```

Nunca modifiques una migración de Flyway que ya está en `develop` — crea una nueva.

**Docker Desktop no arranca en Windows**
Falta WSL2. En PowerShell como administrador: `wsl --install`, reinicia y vuelve a abrir Docker Desktop.
