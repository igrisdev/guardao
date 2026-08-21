/**
 * GUA-36 — Sedes del negocio.
 *
 * El negocio no viaja en ninguna de estas llamadas: el backend lo saca del
 * token (ADR-004). Por eso no hay ningun businessId en las firmas de abajo, y
 * no es un olvido.
 */
import { api } from "./http-client";

export interface Location {
  id: string;
  name: string;
  address: string | null;
  city: string | null;
  active: boolean;
  createdAt: string;
}

export interface LocationPayload {
  name: string;
  address?: string | null;
  city?: string | null;
}

const BASE = "/api/v1/locations";

export function listLocations(soloActivas = false): Promise<Location[]> {
  return api.get<Location[]>(BASE, { query: { activas: soloActivas } });
}

export function createLocation(payload: LocationPayload): Promise<Location> {
  return api.post<Location>(BASE, payload);
}

export function updateLocation(id: string, payload: LocationPayload): Promise<Location> {
  return api.put<Location>(`${BASE}/${id}`, payload);
}

/**
 * Cierra la sede. No la borra: el backend la desactiva, porque su historial de
 * citas cuelga de ella. Responde 409 con LAST_ACTIVE_LOCATION si es la unica
 * que queda abierta.
 */
export function deactivateLocation(id: string): Promise<void> {
  return api.delete<void>(`${BASE}/${id}`);
}

export function reactivateLocation(id: string): Promise<Location> {
  return api.post<Location>(`${BASE}/${id}/reactivate`);
}
