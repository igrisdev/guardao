package com.guardao.backend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * GUA-17 — Base de los tests de integracion.
 *
 * Levanta el contexto completo contra el Postgres de Testcontainers y con
 * el perfil test, donde Flyway construye el esquema desde cero en cada
 * ejecucion.
 *
 * Al compartir esta misma configuracion, Spring reutiliza el contexto
 * cacheado entre clases de test: el contenedor se levanta una vez por
 * ejecucion de la suite, no una por clase.
 *
 * Uso: class MiTest extends IntegrationTest { ... }
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestcontainer.class)
public abstract class IntegrationTest {
}
