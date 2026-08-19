/**
 * Login, registro y logout. Unico lugar que sabe que estos endpoints son
 * publicos (auth: false) y que la sesion se arma con lo que devuelve
 * SessionResponse (ver AuthController en el backend). Los componentes no
 * deberian llamar a /api/v1/auth/* directamente.
 */
import { api, resetSessionExpiredNotification } from "./http-client";
import { tokenStorage, type SessionInfo, type SessionTokens } from "./token-storage";

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegisterPayload {
  businessName: string;
  slug: string;
  locationName: string;
  address?: string;
  city?: string;
  email: string;
  password: string;
}

function startSession(session: SessionTokens): SessionInfo {
  tokenStorage.save(session);
  // Una sesion nueva reabre la posibilidad de notificar si un refresh
  // futuro falla; si no se resetea, un segundo logout silencioso no avisaria.
  resetSessionExpiredNotification();
  return tokenStorage.getSession()!;
}

export async function login(credentials: LoginCredentials): Promise<SessionInfo> {
  const session = await api.post<SessionTokens>("/api/v1/auth/login", credentials, {
    auth: false,
  });
  return startSession(session);
}

export async function register(payload: RegisterPayload): Promise<SessionInfo> {
  const session = await api.post<SessionTokens>("/api/v1/auth/register", payload, {
    auth: false,
  });
  return startSession(session);
}

/**
 * No existe endpoint de logout en el backend: la sesion es JWT sin estado
 * (no hay nada que invalidar del lado del servidor), asi que cerrarla es
 * borrar los tokens del cliente.
 */
export function logout(): void {
  tokenStorage.clear();
}

export function getCurrentSession(): SessionInfo | null {
  return tokenStorage.getSession();
}

export function isAuthenticated(): boolean {
  return tokenStorage.getAccessToken() !== null && tokenStorage.getSession() !== null;
}
