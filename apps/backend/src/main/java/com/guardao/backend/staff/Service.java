package com.guardao.backend.staff;

import com.guardao.backend.shared.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * GUA-31 — Servicio que se presta en una sede: corte, barba, tinturado.
 *
 * Cuelga de la sede porque el precio y la duracion cambian entre una y otra:
 * el mismo corte no vale igual en el centro que en el norte.
 *
 * Dos reglas que no son de forma sino de funcionamiento:
 *
 * - El precio es un entero en pesos, sin decimales ni coma flotante
 *   (Tech Spec 3.1). Con double, sumar veinte citas de $23.500 no da el mismo
 *   total dos veces.
 * - La duracion va en pasos de 30 minutos. La agenda y el calculo de
 *   disponibilidad (GUA-35) se dibujan en bloques de media hora: un servicio
 *   de 45 dejaria huecos que ningun otro puede ocupar y descuadraria la
 *   rejilla. La base tambien lo exige con service_duration_half_hour, asi que
 *   la validacion de ServiceRequest no es la unica defensa: es la que da un
 *   mensaje que se puede mostrar en el formulario.
 *
 * Lo que se guarda aqui es el precio y la duracion de HOY. Al agendar, la
 * cita se queda con su propia copia (ADR-010): subir el precio manana no
 * cambia lo que ya se pacto con un cliente.
 */
@Entity
@Table(name = "service")
// GUA-22 — Mismo aislamiento que Staff: la tabla no tiene business_id, y la
// pertenencia se resuelve por la sede (ADR-004).
@Filter(name = TenantContext.FILTER_NAME,
        condition = "location_id in (select sede.id from location sede"
                + " where sede.business_id = :businessId)")
public class Service {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Sede que lo presta. No se puede cambiar: las citas ya atendidas lo
     * referencian junto con su sede, y moverlo las dejaria inconsistentes.
     */
    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Pesos colombianos, entero. Nunca decimal (Tech Spec 3.1). */
    @Column(name = "price", nullable = false)
    private int price;

    /** Siempre multiplo de 30. Ver el javadoc de la clase. */
    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    /**
     * Un servicio que se deja de ofrecer se desactiva; no se borra, porque
     * las citas que ya lo usaron lo referencian con ON DELETE RESTRICT y el
     * historial de ingresos por servicio quedaria roto.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requerido por JPA. */
    protected Service() {
    }

    public Service(UUID locationId, String name, int price, int durationMin) {
        this.locationId = locationId;
        this.name = name;
        this.price = price;
        this.durationMin = durationMin;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(int durationMin) {
        this.durationMin = durationMin;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Service otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
