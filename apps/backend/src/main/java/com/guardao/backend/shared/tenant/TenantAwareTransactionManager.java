package com.guardao.backend.shared.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * GUA-22 — Enciende el filtro de negocio en cuanto se abre una transaccion.
 *
 * Es la pieza que vuelve automatico el aislamiento: cualquier lectura ocurre
 * dentro de una transaccion, asi que filtrar aqui cubre todo sin que nadie
 * tenga que acordarse en cada consulta (ADR-004).
 *
 * Por que aqui y no en un aspecto sobre los repositorios, que seria lo
 * primero que uno intenta: Spring Data no aplica sus transacciones con el
 * interceptor normal de Spring, sino con uno propio que mete en el proxy del
 * repositorio. Un aspecto externo no puede colocarse por dentro de ese
 * interceptor de manera confiable, y termina corriendo antes de que exista la
 * sesion. Spring entonces crea una temporal para atenderlo, el filtro se
 * enciende ahi, y la consulta real sale despues por otra sesion, sin filtrar.
 *
 * Lo grave de ese camino es que no falla ni avisa: el aislamiento parece
 * puesto y no lo esta. Se detecto porque el test que recorre una peticion
 * HTTP completa devolvio las sedes del otro negocio.
 *
 * Aqui, en cambio, super.doBegin ya dejo la sesion creada y atada a la
 * transaccion, de modo que el filtro se enciende sobre la misma sesion que
 * despues ejecuta las consultas.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    public TenantAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        if (!TenantContext.hasTenant()) {
            // Registro, login, endpoints publicos y tareas programadas. Ver
            // TenantContext para por que en esos casos no debe filtrarse.
            return;
        }

        sesionDeLaTransaccion().ifPresent(session -> session
                .enableFilter(TenantContext.FILTER_NAME)
                .setParameter(TenantContext.PARAM_BUSINESS_ID, TenantContext.require()));
    }

    private java.util.Optional<Session> sesionDeLaTransaccion() {
        Object recurso = TransactionSynchronizationManager.getResource(getEntityManagerFactory());

        if (recurso instanceof EntityManagerHolder holder) {
            EntityManager entityManager = holder.getEntityManager();
            return java.util.Optional.of(entityManager.unwrap(Session.class));
        }

        return java.util.Optional.empty();
    }
}
