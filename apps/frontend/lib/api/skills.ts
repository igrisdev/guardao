/**
 * GUA-38 — Que barbero sabe hacer que servicio.
 *
 * Asignar y revocar son idempotentes en el backend: marcar dos veces la misma
 * casilla no falla ni duplica nada. Por eso la pantalla puede mandar la
 * peticion en cuanto se pulsa, sin llevar la cuenta de lo que ya mando.
 */
import { api } from "./http-client";
import type { Service } from "./services";
import type { Staff } from "./staff";

const base = (locationId: string) => `/api/v1/locations/${locationId}`;

/** Los servicios que sabe hacer un barbero. */
export function listSkillsOfStaff(locationId: string, staffId: string): Promise<Service[]> {
  return api.get<Service[]>(`${base(locationId)}/staff/${staffId}/skills`);
}

/** Los barberos que atienden un servicio. */
export function listStaffOfService(locationId: string, serviceId: string): Promise<Staff[]> {
  return api.get<Staff[]>(`${base(locationId)}/services/${serviceId}/staff`);
}

export function assignSkill(
  locationId: string,
  staffId: string,
  serviceId: string,
): Promise<void> {
  return api.put<void>(`${base(locationId)}/staff/${staffId}/skills/${serviceId}`);
}

export function revokeSkill(
  locationId: string,
  staffId: string,
  serviceId: string,
): Promise<void> {
  return api.delete<void>(`${base(locationId)}/staff/${staffId}/skills/${serviceId}`);
}
