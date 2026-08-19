/**
 * Guarda accessToken, refreshToken y los datos de sesion que vienen en el
 * SessionResponse del backend (login/register/refresh). No hay endpoint
 * /me: userId, businessId, businessSlug y role solo se obtienen ahi, asi
 * que se guardan junto a los tokens en vez de derivarse en otro lado.
 *
 * localStorage y no cookies: el backend no las usa (CORS con
 * allowCredentials=false, el token viaja en el header Authorization), asi
 * que una cookie de sesion no tendria con que trabajar del lado del server.
 *
 * IMPORTANTE: nunca hacer console.log de accessToken ni refreshToken.
 */

const ACCESS_TOKEN_KEY = "guardao.accessToken";
const REFRESH_TOKEN_KEY = "guardao.refreshToken";
const SESSION_KEY = "guardao.session";

export interface SessionInfo {
  userId: string;
  businessId: string | null;
  businessSlug: string | null;
  role: string;
  /** epoch ms en el que expira el accessToken actual. */
  expiresAt: number;
}

export interface SessionTokens {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  userId: string;
  businessId: string | null;
  businessSlug: string | null;
  role: string;
}

function isBrowser(): boolean {
  return typeof window !== "undefined";
}

function readSession(): SessionInfo | null {
  if (!isBrowser()) return null;
  const raw = window.localStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as SessionInfo;
  } catch {
    return null;
  }
}

export const tokenStorage = {
  getAccessToken(): string | null {
    if (!isBrowser()) return null;
    return window.localStorage.getItem(ACCESS_TOKEN_KEY);
  },

  getRefreshToken(): string | null {
    if (!isBrowser()) return null;
    return window.localStorage.getItem(REFRESH_TOKEN_KEY);
  },

  getSession(): SessionInfo | null {
    return readSession();
  },

  /** Sesion nueva: login, register o refresh exitoso. */
  save(session: SessionTokens): void {
    if (!isBrowser()) return;
    window.localStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken);
    window.localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken);
    const info: SessionInfo = {
      userId: session.userId,
      businessId: session.businessId,
      businessSlug: session.businessSlug,
      role: session.role,
      expiresAt: Date.now() + session.expiresInSeconds * 1000,
    };
    window.localStorage.setItem(SESSION_KEY, JSON.stringify(info));
  },

  clear(): void {
    if (!isBrowser()) return;
    window.localStorage.removeItem(ACCESS_TOKEN_KEY);
    window.localStorage.removeItem(REFRESH_TOKEN_KEY);
    window.localStorage.removeItem(SESSION_KEY);
  },
};
