package com.guardao.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * GUA-17 — Base de datos efimera para los tests de integracion.
 *
 * Postgres real en contenedor, no una base en memoria: H2 no soporta
 * btree_gist ni las restricciones EXCLUDE, y probar contra ella daria
 * falsa confianza justo en lo mas importante (Tech Spec 11, ADR-003).
 *
 * La imagen es la misma que usa docker-compose en local, para que un test
 * verde no dependa de una version de Postgres distinta a la de desarrollo.
 *
 * @ServiceConnection inyecta url, usuario y password del contenedor en el
 * datasource; por eso application-test.yml no declara ninguno.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    static final String IMAGEN = "postgres:16";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGEN);
    }
}
