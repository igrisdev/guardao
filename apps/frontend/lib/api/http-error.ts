/**
 * Forma del error tal como lo entrega el backend (ApiError / Tech Spec 5.1),
 * emitida tanto por el @RestControllerAdvice (GlobalExceptionHandler) como
 * por los 401/403 de la cadena de filtros (SecurityErrorResponder). Ambos
 * caminos usan el mismo formato, asi que no hace falta reconciliar dos
 * estructuras distintas: solo envolverla con el status HTTP.
 */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  timestamp?: string;
}

/**
 * Error normalizado que reciben los componentes. Nunca deberian inspeccionar
 * un Response ni la forma interna del ApiError del backend: solo status,
 * code, message y details.
 */
export class HttpError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details?: Record<string, unknown>;
  readonly timestamp?: string;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = "HttpError";
    this.status = status;
    this.code = body.code;
    this.details = body.details;
    this.timestamp = body.timestamp;
  }
}

/** No hubo respuesta del servidor: red caida, backend abajo, CORS, etc. */
export function networkError(): HttpError {
  return new HttpError(0, {
    code: "NETWORK_ERROR",
    message: "No se pudo conectar con el servidor",
  });
}
