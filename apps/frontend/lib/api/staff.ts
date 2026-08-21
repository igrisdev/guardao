/**
 * GUA-37 — Barberos de una sede, y su acceso al dashboard.
 *
 * Todas las rutas cuelgan de la sede porque un barbero pertenece a una sede
 * concreta, no al negocio entero. La sede activa la pone el selector del
 * dashboard (ver components/dashboard/active-location.tsx).
 */
import { api } from "./http-client";

export interface Staff {
  id: string;
  locationId: string;
  name: string;
  active: boolean;
  createdAt: string;
}

export interface StaffPayload {
  name: string;
}

/**
 * Acceso al dashboard de un barbero. Lo crea GUA-23, que vive en otra ruta
 * (/api/v1/staff-accounts) porque es un usuario, no un barbero.
 */
export interface StaffAccount {
  userId: string;
  staffId: string;
  email: string;
  role: string;
  active: boolean;
  createdAt: string;
}

export interface StaffAccountPayload {
  staffId: string;
  email: string;
  password: string;
}

const base = (locationId: string) => `/api/v1/locations/${locationId}/staff`;

export function listStaff(locationId: string, soloActivos = false): Promise<Staff[]> {
  return api.get<Staff[]>(base(locationId), { query: { activos: soloActivos } });
}

export function createStaff(locationId: string, payload: StaffPayload): Promise<Staff> {
  return api.post<Staff>(base(locationId), payload);
}

export function updateStaff(
  locationId: string,
  staffId: string,
  payload: StaffPayload,
): Promise<Staff> {
  return api.put<Staff>(`${base(locationId)}/${staffId}`, payload);
}

/** Da de baja al barbero. Lo desactiva, no lo borra: sus citas lo referencian. */
export function deactivateStaff(locationId: string, staffId: string): Promise<void> {
  return api.delete<void>(`${base(locationId)}/${staffId}`);
}

export function reactivateStaff(locationId: string, staffId: string): Promise<Staff> {
  return api.post<Staff>(`${base(locationId)}/${staffId}/reactivate`);
}

/**
 * Le crea el login a un barbero (GUA-23).
 *
 * OJO: esta ruta no cuelga de la sede, y es a proposito. Lo que se crea es un
 * usuario del negocio, y el negocio sale del token; el barbero se identifica
 * por su staffId dentro del cuerpo.
 *
 * Responde 409 si el barbero ya tenia acceso o si el correo esta en uso.
 */
export function createStaffAccount(payload: StaffAccountPayload): Promise<StaffAccount> {
  return api.post<StaffAccount>("/api/v1/staff-accounts", payload);
}
