-- =====================================================================
-- GUA-24 — Los super-admin de Guardao no pertenecen a ninguna barberia
--
-- El esquema inicial exigia que todo usuario colgara de un negocio, lo que
-- dejaba a los super-admin sin donde ir: Guardao no es una barberia, es la
-- plataforma. Meterlos en una fila de business inventada habria sido peor
-- que el problema, porque ese negocio ficticio saldria en los listados y
-- necesitaria slug, codigo de referido y suscripcion como cualquier otro.
--
-- Se corrige aqui y no editando V1 porque esa migracion ya esta en develop
-- (ADR-007).
-- =====================================================================

ALTER TABLE app_user ALTER COLUMN business_id DROP NOT NULL;

-- La columna queda opcional, pero no libre: cada rol tiene una sola forma
-- valida de llenarla. Un OWNER o un STAFF sin negocio no tendrian datos que
-- ver, y un SUPER_ADMIN atado a una barberia dejaria de ser interno de
-- Guardao para volverse empleado de un cliente.
ALTER TABLE app_user ADD CONSTRAINT app_user_business_only_for_tenant_roles CHECK (
    (role =  'SUPER_ADMIN' AND business_id IS NULL) OR
    (role <> 'SUPER_ADMIN' AND business_id IS NOT NULL)
);
