# ADR-011 — Jackson 3 como serializador JSON

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

Spring Boot 4 cambió de Jackson 2 a **Jackson 3**, y con ese salto cambió el paquete raíz de la librería:

| Componente | Jackson 2 | Jackson 3 |
|---|---|---|
| `ObjectMapper`, serializadores, módulos | `com.fasterxml.jackson.databind` | `tools.jackson.databind` |
| Streaming (`JsonParser`, `JsonGenerator`) | `com.fasterxml.jackson.core` | `tools.jackson.core` |
| **Anotaciones** (`@JsonInclude`, `@JsonProperty`, `@JsonIgnore`) | `com.fasterxml.jackson.annotation` | **igual, no cambió** |

El problema no es el cambio en sí, sino cómo se manifiesta: **Jackson 2 sigue estando en el classpath**, arrastrado transitivamente por springdoc-openapi y por nimbus-jose-jwt. Así que escribir el import de Jackson 2 **compila sin ningún error ni aviso**.

Esto ya ocurrió una vez, en GUA-12:

```java
import com.fasterxml.jackson.databind.ObjectMapper;  // Jackson 2: compila
```

```
APPLICATION FAILED TO START
Parameter 0 of constructor in SecurityErrorResponder required a bean of type
'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```

Spring registra un bean de `tools.jackson.databind.ObjectMapper`. El de Jackson 2 existe como clase pero nadie lo publica como bean. Un import equivocado que compila es más caro que uno que no compila: el fallo aparece al arrancar, lejos de la línea que lo causó, y el mensaje de error no menciona en ningún momento que haya dos Jackson en juego.

## Decisión

**Todo el código de Guardao usa Jackson 3. Nunca se importa `com.fasterxml.jackson.databind` ni `com.fasterxml.jackson.core`.**

La única excepción son las anotaciones, que siguen en `com.fasterxml.jackson.annotation` por decisión de los propios mantenedores de Jackson. No es un descuido nuestro.

```java
// Correcto
import tools.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;

// Incorrecto: compila, pero no hay bean de este tipo
import com.fasterxml.jackson.databind.ObjectMapper;
```

En la práctica, casi nunca hace falta inyectar el `ObjectMapper`: Spring serializa las respuestas de los controladores por su cuenta. Se necesita solo cuando se escribe en la respuesta HTTP a mano, como en `SecurityErrorResponder`, que responde desde la cadena de filtros donde no hay controlador.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Forzar Jackson 2 en todo el proyecto** | Ir contra el valor por defecto del framework. Habría que registrar el bean a mano y renunciar a la autoconfiguración de Spring Boot |
| **Excluir Jackson 2 del classpath** | No se puede: springdoc y nimbus-jose-jwt lo necesitan internamente. La colisión es inevitable, hay que convivir con ella |
| **No documentarlo y confiar en la memoria** | Somos cuatro y el import equivocado autocompleta primero en el IDE. Los cuatro nos lo íbamos a encontrar |

## Consecuencias

**Ganamos**

- Usamos el camino soportado por Spring Boot 4, con toda su autoconfiguración
- Jackson 3 convirtió sus excepciones en no verificadas: menos `try/catch` de ruido
- Queda un lugar al que apuntar cuando alguien tropiece con esto

**Aceptamos**

- Conviven dos versiones de Jackson en el classpath, y ninguna herramienta nos avisa si importamos la equivocada
- Los ejemplos de Stack Overflow y la mayoría de tutoriales todavía son de Jackson 2: copiar y pegar va a fallar de esta forma exacta
- Los agentes de IA tienden a escribir el import de Jackson 2, porque es lo que domina en su entrenamiento

## Regla para el equipo

> Si el IDE te autocompleta un import que empieza por `com.fasterxml.jackson.databind` o `com.fasterxml.jackson.core`, está mal. Debe ser `tools.jackson`. Las anotaciones son la única excepción.

Cómo detectarlo antes de abrir el PR:

```bash
grep -rn "com.fasterxml.jackson.databind\|com.fasterxml.jackson.core" apps/backend/src/
```

Si devuelve algo, hay que corregirlo. Vale la pena convertirlo en un paso del pipeline de CI (GUA-18).
