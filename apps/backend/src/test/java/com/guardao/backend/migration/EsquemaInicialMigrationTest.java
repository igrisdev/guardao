package com.guardao.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardao.backend.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GUA-17 — Flyway levanta el esquema completo desde cero.
 *
 * Es uno de los tres tests que bloquean el merge (Tech Spec 11). El
 * contenedor nace vacio en cada ejecucion, asi que lo que se verifica aqui
 * es exactamente lo que le pasara a una base nueva de staging o produccion.
 *
 * Verifica la estructura, no el comportamiento: que la restriccion contra
 * doble reserva exista. Que ademas rechace una reserva solapada se prueba
 * en la Etapa 3, cuando exista el motor de citas.
 */
class EsquemaInicialMigrationTest extends IntegrationTest {

    /** Las 20 tablas del modelo de datos (plan seccion 4, GUA-10). */
    private static final List<String> TABLAS_ESPERADAS = List.of(
            "business", "location", "staff", "service", "skill",
            "app_user", "schedule", "block", "loyalty", "client",
            "appointment", "subscription", "product", "orders", "item",
            "payment", "gateway", "social", "gallery", "notification");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("todas las migraciones quedan aplicadas sin errores")
    void todasLasMigracionesAplicadas() {
        // Se pregunta por las fallidas en vez de por una version concreta: asi
        // la comprobacion sigue sirviendo cuando se agregue la siguiente
        List<String> fallidas = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = false",
                String.class);

        assertThat(fallidas).as("Flyway no debe dejar ninguna migracion a medias").isEmpty();

        List<String> aplicadas = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL",
                String.class);

        assertThat(aplicadas)
                .as("el esquema inicial y el ajuste de los super-admin deben estar puestos")
                .contains("1", "2");
    }

    @Test
    @DisplayName("crea las 20 tablas del modelo y ninguna de mas")
    void creaLasTablasDelModelo() {
        List<String> tablas = jdbc.queryForList(
                """
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_type = 'BASE TABLE'
                   AND table_name <> 'flyway_schema_history'
                """,
                String.class);

        assertThat(tablas)
                .as("el esquema del plan son 20 tablas; sobra o falta alguna")
                .containsExactlyInAnyOrderElementsOf(TABLAS_ESPERADAS);
    }

    @Test
    @DisplayName("instala btree_gist, que necesita la restriccion EXCLUDE")
    void instalaBtreeGist() {
        Integer instalada = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'",
                Integer.class);

        assertThat(instalada)
                .as("sin btree_gist no se puede crear la restriccion contra doble reserva (ADR-003)")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("deja viva la restriccion contra doble reserva")
    void restriccionContraDobleReserva() {
        Integer existe = jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM pg_constraint
                 WHERE conname = 'appointment_no_overlap'
                   AND contype = 'x'
                """,
                Integer.class);

        assertThat(existe)
                .as("appointment_no_overlap es la ultima linea de defensa contra la doble reserva (ADR-003)")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("deja viva la restriccion de stock no negativo")
    void restriccionDeStockNoNegativo() {
        Integer existe = jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM pg_constraint
                 WHERE conname = 'product_stock_non_negative'
                   AND contype = 'c'
                """,
                Integer.class);

        assertThat(existe)
                .as("product_stock_non_negative evita vender por debajo de cero en compras simultaneas")
                .isEqualTo(1);
    }
}
