export { api, setSessionExpiredHandler } from "./http-client";
export { HttpError } from "./http-error";
export type { ApiErrorBody } from "./http-error";
export { login, register, logout, getCurrentSession, isAuthenticated } from "./auth";
export type { LoginCredentials, RegisterPayload } from "./auth";
export type { SessionInfo } from "./token-storage";

// Etapa 2 — Configuracion del negocio (GUA-36 a GUA-39)
export {
  listLocations,
  createLocation,
  updateLocation,
  deactivateLocation,
  reactivateLocation,
} from "./locations";
export type { Location, LocationPayload } from "./locations";

export {
  listStaff,
  createStaff,
  updateStaff,
  deactivateStaff,
  reactivateStaff,
  createStaffAccount,
} from "./staff";
export type { Staff, StaffPayload, StaffAccount, StaffAccountPayload } from "./staff";

export {
  listServices,
  createService,
  updateService,
  deactivateService,
  reactivateService,
} from "./services";
export type { Service, ServicePayload } from "./services";

export { listSkillsOfStaff, listStaffOfService, assignSkill, revokeSkill } from "./skills";

export {
  getLocationSchedule,
  replaceLocationSchedule,
  getStaffSchedule,
  replaceStaffSchedule,
  clearStaffSchedule,
} from "./schedule";
export type { ScheduleSlot, ScheduleSlotPayload } from "./schedule";

export { listBlocks, createBlock, updateBlock, deleteBlock } from "./blocks";
export type { Block, BlockPayload } from "./blocks";
