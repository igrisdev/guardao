/**
 * Traduce un error del backend a lo que necesita un formulario.
 *
 * El contrato del backend (Tech Spec 5.1) trae dos formas distintas y conviene
 * no confundirlas:
 *
 * - VALIDATION_ERROR, que sale de las anotaciones de validacion, viene con
 *   details.fields: un mensaje por campo, listo para pintar debajo del campo
 * - el resto —OVERLAPPING_SCHEDULE, EMAIL_TAKEN, LAST_ACTIVE_LOCATION— es una
 *   regla de negocio sobre el formulario entero, y va arriba
 *
 * La decision se toma por el code, nunca por el texto del mensaje: los
 * mensajes cambian, los codigos no.
 */
import { HttpError } from "./http-error";

/** Mensajes por campo. Vacio cuando el error no es de validacion. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof HttpError)) return {};

  const fields = error.details?.fields;
  if (!fields || typeof fields !== "object") return {};

  const resultado: Record<string, string> = {};
  for (const [campo, mensaje] of Object.entries(fields as Record<string, unknown>)) {
    if (typeof mensaje === "string") resultado[campo] = mensaje;
  }
  return resultado;
}

/**
 * Mensaje para mostrar arriba del formulario.
 *
 * Devuelve null cuando el error ya se explico campo por campo: repetir "hay
 * campos con datos invalidos" encima de tres campos ya marcados en rojo solo
 * hace ruido.
 */
export function formErrorMessage(error: unknown): string | null {
  if (!(error instanceof HttpError)) {
    return "Ocurrio un error inesperado";
  }

  if (Object.keys(fieldErrors(error)).length > 0) {
    return null;
  }

  return error.message;
}
