package com.guardao.backend.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.guardao.backend.shared.tenant.TenantContext;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * GUA-19 — Sede de un negocio. Un negocio puede tener varias, y casi todo lo
 * operativo (staff, servicios, horarios, citas) cuelga de la sede, no del
 * negocio.
 *
 * businessId se guarda como identificador y no como relacion @ManyToOne: es
 * la columna por la que se filtra en cada consulta (ADR-004), y como
 * relacion Hibernate terminaria trayendo el negocio completo cada vez sin
 * que nadie lo pida.
 */
@Entity
@Table(name = "location")
// GUA-22 — Solo se ven las filas del negocio de la peticion. El filtro se
// enciende solo (TenantFilterActivator); aqui no hay nada que recordar.
@Filter(name = TenantContext.FILTER_NAME, condition = "business_id = :businessId")
public class Location {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "city", length = 80)
    private String city;

    /** Una sede cerrada se desactiva; no se borra, porque tiene historial de citas. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requerido por JPA. */
    protected Location() {
    }

    public Location(UUID businessId, String name) {
        this.businessId = businessId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
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
        if (!(other instanceof Location otra)) {
            return false;
        }
        return id != null && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
