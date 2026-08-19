/**
 * URL base de la API. En local apunta al backend de Spring Boot (puerto
 * 8080, ver README.md). Configurable con NEXT_PUBLIC_API_URL para
 * staging/produccion.
 */
export const API_BASE_URL =
  (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/\/$/, "");
