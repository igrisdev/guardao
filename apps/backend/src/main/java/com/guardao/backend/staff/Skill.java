package com.guardao.backend.staff;

import com.guardao.backend.shared.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

/**
 * GUA-31 — Habilidad: que barbero sabe hacer que servicio.
 *
 * Es la tabla intermedia entre Staff y Service. No se modela como una
 * relacion muchos-a-muchos de JPA a proposito, porque eso la volveria
 * invisible desde el codigo y esta relacion no es un detalle de mapeo. La
 * pagina publica filtra por ella (solo ofrece los barberos que saben hacer el
 * servicio que el cliente eligio, Etapa 4) y el motor de disponibilidad la
 * cruza en cada consulta. Como entidad propia se le pueden agregar consultas
 * y, mas adelante, columnas suyas.
 *
 * La base impide duplicados con skill_staff_service_unique: asignar dos veces
 * la misma habilidad no crea una segunda fila.
 *
 * Aqui solo va la entidad. Los endpoints para asignar y quitar habilidades
 * son GUA-32.
 */
@Entity
@Table(name = "skill")
// GUA-22 — La pertenencia se resuelve por el barbero y su sede, dos saltos
// mas alla del negocio (ADR-004).
@Filter(name = TenantContext.FILTER_NAME,
        condition = "staff_id in (select barbero.id from staff barbero"
                + " join location sede on sede.id = barbero.location_id"
                + " where sede.business_id = :businessId)")
public class Skill {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    /** Requerido por JPA. */
    protected Skill() {
    }

    public Skill(UUID staffId, UUID serviceId) {
        this.staffId = staffId;
        this.serviceId = serviceId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Skill otra)) {
            return false;
        }
        return id != null && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
