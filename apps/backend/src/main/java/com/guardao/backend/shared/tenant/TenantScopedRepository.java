package com.guardao.backend.shared.tenant;

import java.io.Serializable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * GUA-22 — Base de los repositorios cuyos datos pertenecen a un negocio.
 *
 * Existe por una razon concreta y poco evidente: Spring Data solo abre una
 * transaccion para los metodos heredados de JpaRepository (findById, findAll,
 * save...). Las consultas derivadas del nombre del metodo, como
 * findByBusinessId o findByEmail, corren SIN transaccion, y como el filtro de
 * negocio se enciende al abrirla, esas consultas salian sin filtrar.
 *
 * Era el caso peor posible: las consultas mas obvias quedaban protegidas y
 * las que uno agrega despues, no. Se descubrio porque un test pedia
 * explicitamente las sedes de otro negocio y se las devolvia.
 *
 * El @Transactional de aqui obliga a que toda consulta, derivada o heredada,
 * abra transaccion y por lo tanto quede filtrada. Heredarlo, en vez de
 * repetir la anotacion en cada repositorio, es lo que evita que a alguien se
 * le olvide en el proximo.
 *
 * Los metodos de escritura no quedan en solo lectura: los suyos declaran su
 * propia transaccion y esa manda sobre esta.
 */
@NoRepositoryBean
@Transactional(readOnly = true)
public interface TenantScopedRepository<T, ID extends Serializable> extends JpaRepository<T, ID> {
}
