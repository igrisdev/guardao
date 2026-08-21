package com.guardao.backend.schedule;

import com.guardao.backend.shared.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

/**
 * GUA-33 — Una franja de horario recurrente: "los sabados de 8 a 12".
 *
 * Una fila es UNA franja, no un dia. Un dia con jornada partida son dos filas
 * del mismo dayOfWeek, y por eso la tabla no tiene una restriccion de unicidad
 * sobre (sede, barbero, dia): el sabado de 8 a 12 y de 14 a 18 es el caso
 * normal en una barberia, no la excepcion.
 *
 * staffId nulo es el horario general de la sede. Con valor, es el horario
 * propio de ese barbero. Segun GUA-33 el horario del barbero vale "dentro del
 * horario general", asi que el motor de disponibilidad los cruza en vez de
 * quedarse solo con el del barbero: nadie puede atender con la sede cerrada.
 *
 * Las horas van en LocalTime, sin zona, y es a proposito (Tech Spec 3.1):
 * "abrimos a las 8" es local a la sede y no cambia si el servidor esta en otro
 * huso. La conversion a un instante concreto ocurre al calcular la
 * disponibilidad de un dia (GUA-35), con la zona del negocio.
 */
@Entity
@Table(name = "schedule")
// GUA-22 — La tabla no tiene business_id: la pertenencia se resuelve por la
// sede, igual que en Staff y Service (ADR-004).
@Filter(name = TenantContext.FILTER_NAME,
        condition = "location_id in (select sede.id from location sede"
                + " where sede.business_id = :businessId)")
public class Schedule {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    /** Nulo = horario general de la sede. Con valor = horario propio de ese barbero. */
    @Column(name = "staff_id", updatable = false)
    private UUID staffId;

    /**
     * 0 = domingo, 6 = sabado, tal como lo define la migracion inicial.
     *
     * No es el orden de java.time.DayOfWeek, que numera de 1 (lunes) a 7
     * (domingo). La conversion entre ambos vive en DayOfWeekCodec, en un solo
     * sitio: hacerla a mano en cada consulta es la clase de error que corre el
     * horario un dia entero sin que nada falle.
     */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    /** Requerido por JPA. */
    protected Schedule() {
    }

    public Schedule(UUID locationId, UUID staffId, short dayOfWeek,
            LocalTime openTime, LocalTime closeTime) {
        this.locationId = locationId;
        this.staffId = staffId;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public short getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Schedule otra)) {
            return false;
        }
        return id != null && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
