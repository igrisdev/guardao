package com.guardao.backend.shared.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * GUA-22 — Reemplaza el gestor de transacciones por uno que enciende el
 * filtro de negocio al abrir cada una.
 *
 * Spring Boot pone un JpaTransactionManager por omision; al declarar este
 * bean se usa el nuestro en su lugar, y con eso el aislamiento pasa a
 * aplicarse en toda la aplicacion sin tocar ningun repositorio ni servicio.
 *
 * Es un punto sensible: si alguien declara otro gestor de transacciones, el
 * aislamiento deja de aplicarse. Los tests de acceso cruzado estan
 * justamente para que eso no pase inadvertido (ADR-004).
 */
@Configuration
public class TenancyConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new TenantAwareTransactionManager(entityManagerFactory);
    }
}
