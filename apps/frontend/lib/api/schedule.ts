/**
 * GUA-39 — Horario semanal de la sede y de cada barbero.
 *
 * El horario se guarda ENTERO, no franja por franja: se manda la semana
 * completa y sustituye a la anterior. Es asi porque las reglas del horario son
 * del conjunto —que dos franjas del mismo dia no se crucen solo se puede
 * comprobar mirandolas todas a la vez— y porque le ahorra a la pantalla llevar
 * la cuenta de que franja es nueva, cual cambio y cual hay que borrar.
 *
 * dayOfWeek va de 0 (domingo) a 6 (sabado), la misma numeracion que
 * Date.getDay(). Las horas son "HH:mm" locales de la sede, sin zona.
 */
import { api } from "./http-client";

export interface ScheduleSlot {
  id: string;
  locationId: string;
  staffId: string | null;
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
}

/** Lo que se manda: sin id, porque las franjas se reemplazan, no se editan. */
export interface ScheduleSlotPayload {
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
}

const base = (locationId: string) => `/api/v1/locations/${locationId}`;

export function getLocationSchedule(locationId: string): Promise<ScheduleSlot[]> {
  return api.get<ScheduleSlot[]>(`${base(locationId)}/schedule`);
}

export function replaceLocationSchedule(
  locationId: string,
  slots: ScheduleSlotPayload[],
): Promise<ScheduleSlot[]> {
  return api.put<ScheduleSlot[]>(`${base(locationId)}/schedule`, { slots });
}

/** Vacio significa que el barbero no tiene horario propio y sigue el de la sede. */
export function getStaffSchedule(locationId: string, staffId: string): Promise<ScheduleSlot[]> {
  return api.get<ScheduleSlot[]>(`${base(locationId)}/staff/${staffId}/schedule`);
}

export function replaceStaffSchedule(
  locationId: string,
  staffId: string,
  slots: ScheduleSlotPayload[],
): Promise<ScheduleSlot[]> {
  return api.put<ScheduleSlot[]>(`${base(locationId)}/staff/${staffId}/schedule`, { slots });
}

/**
 * Le quita el horario propio al barbero, con lo que vuelve a regirse por el de
 * la sede. NO es lo mismo que guardarle una semana vacia, que seria un barbero
 * que no trabaja ningun dia.
 */
export function clearStaffSchedule(locationId: string, staffId: string): Promise<void> {
  return api.delete<void>(`${base(locationId)}/staff/${staffId}/schedule`);
}
