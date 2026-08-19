/**
 * Punto unico de acceso a la API. Ningun componente deberia llamar a
 * fetch() directamente: todo pasa por aqui, que se encarga de adjuntar el
 * JWT, detectar 401, refrescar la sesion y reintentar la peticion original.
 */
import { API_BASE_URL } from "./config";
import { HttpError, networkError, type ApiErrorBody } from "./http-error";
import { tokenStorage, type SessionTokens } from "./token-storage";

type QueryParams = Record<string, string | number | boolean | undefined | null>;

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  query?: QueryParams;
  /**
   * Si la peticion requiere JWT. Por omision true: la mayoria de las rutas
   * estan protegidas (SecurityConfig las deja fuera de la whitelist por
   * defecto). Las rutas publicas (auth, /api/v1/public/**, webhooks) deben
   * pasar auth: false explicitamente para no adjuntar un token que no
   * necesitan.
   */
  auth?: boolean;
  signal?: AbortSignal;
}

/**
 * Refresh compartido entre peticiones concurrentes: si varias piden 401 casi
 * al mismo tiempo, todas esperan la misma promesa en vez de disparar un
 * /auth/refresh por cada una. Se limpia en cuanto resuelve, exitosa o no.
 */
let refreshPromise: Promise<string> | null = null;

/**
 * Que hacer cuando el refresh tambien falla. Por omision manda al login;
 * una app que quiera otro comportamiento (un modal, un store de auth, etc.)
 * puede reemplazarlo con setSessionExpiredHandler.
 */
let onSessionExpired: () => void = () => {
  if (typeof window !== "undefined") {
    // Recarga completa a proposito: limpia cualquier estado en memoria de
    // la sesion vencida. Este modulo no es un componente y no tiene
    // useRouter(); una app que prefiera navegacion sin recarga puede
    // reemplazar este comportamiento con setSessionExpiredHandler.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination -- ver comentario arriba
    window.location.href = "/login";
  }
};

/** Evita redirigir una vez por cada peticion que estaba esperando el refresh fallido. */
let sessionExpiredNotified = false;

export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler;
}

/** Se llama tras un login/register exitoso, para que un refresh fallido futuro vuelva a notificar. */
export function resetSessionExpiredNotification(): void {
  sessionExpiredNotified = false;
}

function notifySessionExpired(): void {
  if (sessionExpiredNotified) return;
  sessionExpiredNotified = true;
  onSessionExpired();
}

function buildUrl(path: string, query?: QueryParams): string {
  const url = new URL(path.startsWith("http") ? path : `${API_BASE_URL}${path}`);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null) {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.toString();
}

function buildHeaders(hasBody: boolean, accessToken: string | null): Headers {
  const headers = new Headers({ Accept: "application/json" });
  if (hasBody) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  return headers;
}

async function parseErrorBody(response: Response): Promise<ApiErrorBody> {
  try {
    const data = await response.json();
    if (data && typeof data.code === "string" && typeof data.message === "string") {
      return data as ApiErrorBody;
    }
  } catch {
    // cuerpo vacio o no-JSON: se cae al mensaje generico de abajo
  }
  return { code: "UNKNOWN_ERROR", message: "Ocurrio un error inesperado" };
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new HttpError(response.status, await parseErrorBody(response));
  }
  if (response.status === 204) return undefined as T;

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function doFetch(
  path: string,
  method: string,
  query: QueryParams | undefined,
  body: unknown,
  accessToken: string | null,
  signal: AbortSignal | undefined,
): Promise<Response> {
  try {
    return await fetch(buildUrl(path, query), {
      method,
      headers: buildHeaders(body !== undefined, accessToken),
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch {
    throw networkError();
  }
}

/**
 * Unico lugar donde se llama a /auth/refresh. auth: false porque el
 * refresh viaja con el refreshToken en el body, no como Bearer; y al ser
 * auth: false, rawRequest ni siquiera evalua la rama de reintento por 401,
 * asi que un refresh que falla no puede disparar otro refresh.
 *
 * No atrapa el error: si /auth/refresh responde con su propio 401 (p. ej.
 * INVALID_REFRESH_TOKEN), handleResponse ya lo convierte en HttpError, y
 * ese es exactamente el error que le conviene ver al que llamo, en vez del
 * 401 generico de la peticion original.
 */
async function doRefresh(refreshToken: string): Promise<string> {
  const session = await rawRequest<SessionTokens>("/api/v1/auth/refresh", {
    method: "POST",
    body: { refreshToken },
    auth: false,
  });
  tokenStorage.save(session);
  return session.accessToken;
}

function refreshAccessToken(refreshToken: string): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = doRefresh(refreshToken).finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function rawRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, query, auth = true, signal } = options;

  const accessToken = auth ? tokenStorage.getAccessToken() : null;
  const response = await doFetch(path, method, query, body, accessToken, signal);

  // Solo se intenta refrescar peticiones que iban autenticadas: una ruta
  // publica que responde 401 (p. ej. credenciales invalidas en /login) no
  // tiene refresh que hacer, es un error de negocio como cualquier otro.
  if (response.status === 401 && auth) {
    const refreshToken = tokenStorage.getRefreshToken();
    if (!refreshToken) {
      notifySessionExpired();
      throw new HttpError(response.status, await parseErrorBody(response));
    }

    let tokenToRetryWith: string;
    const currentAccessToken = tokenStorage.getAccessToken();
    if (currentAccessToken && currentAccessToken !== accessToken) {
      // Otra peticion concurrente ya refresco la sesion mientras esta
      // esperaba su propio 401: el token guardado ya no es el que fallo,
      // asi que alcanza con reintentar con el vigente, sin refrescar otra
      // vez (evita refrescos de mas cuando varias peticiones expiran casi
      // al tiempo pero no llegan a compartir el mismo refreshPromise).
      tokenToRetryWith = currentAccessToken;
    } else {
      try {
        // Compartido entre todas las peticiones que expiran a la vez: una
        // sola llamada real a /auth/refresh, el resto espera esta promesa.
        tokenToRetryWith = await refreshAccessToken(refreshToken);
      } catch (err) {
        tokenStorage.clear();
        notifySessionExpired();
        throw err;
      }
    }

    // Un solo reintento, con fetch directo (no vuelve a pasar por
    // rawRequest): si por alguna razon el token nuevo tambien recibe 401,
    // se propaga tal cual, sin volver a intentar refrescar.
    const retryResponse = await doFetch(path, method, query, body, tokenToRetryWith, signal);
    return handleResponse<T>(retryResponse);
  }

  return handleResponse<T>(response);
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) =>
    rawRequest<T>(path, { ...options, method: "GET" }),

  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    rawRequest<T>(path, { ...options, method: "POST", body }),

  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    rawRequest<T>(path, { ...options, method: "PUT", body }),

  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    rawRequest<T>(path, { ...options, method: "PATCH", body }),

  delete: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) =>
    rawRequest<T>(path, { ...options, method: "DELETE" }),
};
