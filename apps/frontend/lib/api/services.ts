/**
 * GUA-38 — Servicios que ofrece una sede.
 *
 * price va en pesos enteros, sin decimales ni separadores: 25000, no "25.000"
 * ni 25000.0 (Tech Spec 3.1). El formato bonito es cosa de la pantalla.
 *
 * durationMin siempre es multiplo de 30. El backend rechaza cualquier otra
 * cosa con 400 y el campo durationMin señalado, asi que el formulario ofrece
 * un desplegable y no un campo libre.
 */
import { api } from "./http-client";

export interface Service {
  id: string;
  locationId: string;
  name: string;
  price: number;
  durationMin: number;
  active: boolean;
  createdAt: string;
}

export interface ServicePayload {
  name: string;
  price: number;
  durationMin: number;
}

const base = (locationId: string) => `/api/v1/locations/${locationId}/services`;

export function listServices(locationId: string, soloActivos = false): Promise<Service[]> {
  return api.get<Service[]>(base(locationId), { query: { activos: soloActivos } });
}

export function createService(locationId: string, payload: ServicePayload): Promise<Service> {
  return api.post<Service>(base(locationId), payload);
}

export function updateService(
  locationId: string,
  serviceId: string,
  payload: ServicePayload,
): Promise<Service> {
  return api.put<Service>(`${base(locationId)}/${serviceId}`, payload);
}

/** Retira el servicio del catalogo. Lo desactiva, no lo borra. */
export function deactivateService(locationId: string, serviceId: string): Promise<void> {
  return api.delete<void>(`${base(locationId)}/${serviceId}`);
}

export function reactivateService(locationId: string, serviceId: string): Promise<Service> {
  return api.post<Service>(`${base(locationId)}/${serviceId}/reactivate`);
}
