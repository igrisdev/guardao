package com.guardao.backend.schedule;

import com.guardao.backend.shared.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

/**
 * GUA-34 — Un rato en el que un barbero no atiende: vacaciones, una cita
 * medica, la tarde del cumpleaños de su hija.
 *
 * Es lo que no cabe en el horario semanal. El horario dice lo que se repite
 * todas las semanas; el bloqueo dice lo que pasa una vez, en fechas
 * concretas, y por eso va en timestamptz y no en un dia de la semana.
 *
 * Cuelga del barbero y no de la sede: cerrar la sede entera un dia es otra
 * cosa (se quita ese dia del horario, o se bloquea a cada barbero). Un
 * bloqueo siempre tiene dueño.
 *
 * No se cruza con nada al crearlo. Dos bloqueos que se solapan son
 * inofensivos —el barbero sigue igual de no disponible— y prohibirlo obligaria
 * a fusionar rangos a mano cada vez que alguien alarga unas vacaciones.
 */
@Entity
@Table(name = "block")
// GUA-22 — Dos saltos hasta el negocio: bloqueo -> barbero -> sede, igual que
// las habilidades (ADR-004).
@Filter(name = TenantContext.FILTER_NAME,
        condition = "staff_id in (select barbero.id from staff barbero"
                + " join location sede on sede.id = barbero.location_id"
                + " where sede.business_id = :businessId)")
public class Block {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    /** Opcional: "vacaciones", "cita medica". Es para el dueño, nadie lo procesa. */
    @Column(name = "reason", length = 200)
    private String reason;

    /** Requerido por JPA. */
    protected Block() {
    }

    public Block(UUID staffId, Instant startAt, Instant endAt, String reason) {
        this.staffId = staffId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Block otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
