package com.guardao.backend.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.guardao.backend.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.support.Repositories;

/**
 * GUA-22 — Ningun repositorio se queda por fuera del aislamiento.
 *
 * Los demas tests comprueban que el filtro funciona; este comprueba que se
 * aplique a todo. Un repositorio que extienda JpaRepository directamente
 * compila, funciona y devuelve datos de todos los negocios sin dar el menor
 * aviso: sus consultas derivadas correrian sin transaccion y por lo tanto sin
 * filtro (ver TenantScopedRepository).
 *
 * Ese descuido es dificil de ver en una revision de codigo, porque la linea
 * que falta se parece muchisimo a la correcta. Aqui rompe el build.
 */
class RepositoriosBajoAislamientoTest extends IntegrationTest {

    @Autowired
    private ListableBeanFactory beanFactory;

    @Test
    @DisplayName("todos los repositorios heredan de TenantScopedRepository")
    void todosLosRepositoriosHeredanDeTenantScopedRepository() {
        Repositories repositorios = new Repositories(beanFactory);

        List<String> incumplen = new ArrayList<>();
        for (Class<?> tipoDeDominio : repositorios) {
            Class<?> interfaz = repositorios.getRequiredRepositoryInformation(tipoDeDominio)
                    .getRepositoryInterface();

            if (!TenantScopedRepository.class.isAssignableFrom(interfaz)) {
                incumplen.add(interfaz.getSimpleName());
            }
        }

        assertThat(incumplen)
                .as("estos repositorios extienden JpaRepository directamente y sus consultas "
                        + "derivadas devolverian datos de cualquier negocio; deben extender "
                        + "TenantScopedRepository")
                .isEmpty();
    }

    @Test
    @DisplayName("hay repositorios que revisar, para que el test anterior no pase en vacio")
    void hayRepositoriosQueRevisar() {
        Repositories repositorios = new Repositories(beanFactory);

        assertThat(repositorios.iterator())
                .as("si no encuentra ninguno, la comprobacion de arriba no esta comprobando nada")
                .hasNext();
    }
}
