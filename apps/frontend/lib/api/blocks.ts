/**
 * GUA-39 — Dias libres, vacaciones y ausencias puntuales de un barbero.
 *
 * startAt y endAt son instantes ISO con zona ("2026-09-01T13:00:00Z"), no
 * horas locales sueltas: un bloqueo es un momento concreto de la linea del
 * tiempo, a diferencia del horario semanal. El formulario recoge fecha y hora
 * locales y las convierte con new Date(...).toISOString().
 */
import { api } from "./http-client";

export interface Block {
  id: string;
  staffId: string;
  startAt: string;
  endAt: string;
  reason: string | null;
}

export interface BlockPayload {
  startAt: string;
  endAt: string;
  reason?: string | null;
}

const base = (locationId: string, staffId: string) =>
  `/api/v1/locations/${locationId}/staff/${staffId}/blocks`;

export function listBlocks(locationId: string, staffId: string): Promise<Block[]> {
  return api.get<Block[]>(base(locationId, staffId));
}

export function createBlock(
  locationId: string,
  staffId: string,
  payload: BlockPayload,
): Promise<Block> {
  return api.post<Block>(base(locationId, staffId), payload);
}

export function updateBlock(
  locationId: string,
  staffId: string,
  blockId: string,
  payload: BlockPayload,
): Promise<Block> {
  return api.put<Block>(`${base(locationId, staffId)}/${blockId}`, payload);
}

/** Este si se borra de verdad: no hay historial que conservar. */
export function deleteBlock(
  locationId: string,
  staffId: string,
  blockId: string,
): Promise<void> {
  return api.delete<void>(`${base(locationId, staffId)}/${blockId}`);
}
