package com.guardao.backend;

import com.guardao.backend.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * GUA-17 — El contexto de Spring arranca completo contra Postgres real.
 *
 * Antes heredaba solo de @SpringBootTest y buscaba la base en localhost:
 * pasaba en la maquina de quien tuviera docker-compose arriba y fallaba en
 * CI. Ahora usa el contenedor efimero como el resto de los tests.
 */
class BackendApplicationTests extends IntegrationTest {

	@Test
	@DisplayName("el contexto carga con seguridad, JPA y Flyway configurados")
	void contextLoads() {
	}

}
