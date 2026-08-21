package com.guardao.backend.schedule;

import com.guardao.backend.shared.tenant.TenantScopedRepository;
import java.util.List;
import java.util.UUID;

/**
 * GUA-33 — Acceso a las franjas de horario.
 *
 * Los metodos distinguen "el horario de la sede" de "el horario de un
 * barbero", y no alcanza con filtrar por sede: las franjas del barbero viven
 * en la misma tabla y con el mismo locationId. Lo que las separa es que
 * staffId sea nulo o no, y eso hay que decirlo en cada consulta —
 * findByLocationId a secas devuelve las dos cosas mezcladas, que es justo el
 * error que haria abrir la sede en el horario de un solo barbero.
 */
public interface ScheduleRepository extends TenantScopedRepository<Schedule, UUID> {

    /** Solo el horario general: las franjas sin barbero. */
    List<Schedule> findByLocationIdAndStaffIdIsNullOrderByDayOfWeekAscOpenTimeAsc(UUID locationId);

    /** Solo el horario propio de ese barbero. */
    List<Schedule> findByLocationIdAndStaffIdOrderByDayOfWeekAscOpenTimeAsc(
            UUID locationId, UUID staffId);

    void deleteByLocationIdAndStaffIdIsNull(UUID locationId);

    void deleteByLocationIdAndStaffId(UUID locationId, UUID staffId);
}
