# ADR-009 — Sin IA orientada al cliente final

**Estado:** Aceptado
**Fecha:** Agosto 2026

## Contexto

Los mockups iniciales del producto incluían una sección llamada "Luna IA": un asistente conversacional para clientes. Además, el sistema recibe mensajes entrantes de WhatsApp cuando un cliente responde a una notificación, lo que abre la puerta natural a interpretarlos con un modelo de lenguaje.

## Decisión

**El producto no incluye ningún chatbot ni asistente de IA orientado al cliente final.** La sección "Luna IA" se elimina del alcance.

El webhook de mensajes entrantes de WhatsApp **no interpreta el texto**. Su comportamiento es determinista:

1. Recibe la respuesta del cliente
2. Identifica la cita mediante el `provider_message_id` del mensaje original al que se responde
3. Contesta **siempre lo mismo**: el enlace de gestión de esa cita

Sin clasificación de intención, sin generación de texto, sin modelo de por medio.

## Alternativas consideradas

### Asistente conversacional para reservar por WhatsApp

Descartada para el MVP. Razones:

- **La página pública ya resuelve el problema**, y mejor: el cliente ve el calendario completo con los horarios ocupados de un vistazo. Una conversación por texto es una forma peor de elegir entre veinte horarios
- Un asistente que agenda mal genera un problema real: un cliente esperando a una hora que nadie reservó
- Costo por conversación que escala con el uso, sobre un producto de suscripción baja
- Requiere manejar ambigüedad, cambios de tema y frustración del usuario: un producto entero, no una funcionalidad

### Interpretar la respuesta del cliente para clasificar intención

Descartada. "Cancelar mi cita" interpretado incorrectamente cancela la cita equivocada. Devolver siempre el enlace de gestión deja la decisión en manos del cliente, con la interfaz que ya existe y que valida disponibilidad.

## Consecuencias

**Ganamos**
- Comportamiento predecible: el mismo mensaje entrante produce siempre la misma respuesta
- Cero costo variable de modelos y cero latencia de inferencia
- Ninguna superficie de ataque por inyección de instrucciones a través de mensajes de clientes
- Alcance del MVP más chico, que es lo que permite lanzar

**Aceptamos**
- El cliente que responde por WhatsApp esperando conversar recibe un enlace. Es una experiencia más seca, pero funcional y honesta
- Si la competencia lanza un asistente, quedamos atrás en percepción de modernidad. Se acepta: preferimos una agenda que nunca falla

## Alcance de esta decisión

Esta decisión aplica al **producto**. No prohíbe el uso de IA en el proceso de desarrollo: el equipo usa agentes de código y Claude conectado a Jira. Esas reglas están en [`system-prompt-spec.md`](../system-prompt-spec.md).

## Cuándo revisar

Si más adelante los datos muestran que un porcentaje relevante de clientes responde por WhatsApp intentando gestionar su cita y abandona al recibir el enlace, vale la pena reconsiderarlo — con métricas, no por intuición.
