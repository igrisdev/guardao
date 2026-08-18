-- =====================================================================
-- GUA-10 — Esquema inicial de Guardao
--
-- Reglas transversales (Tech Spec 3.1):
--   - Identificadores: uuid en todas las tablas
--   - Marcas de tiempo: timestamptz siempre, nunca timestamp sin zona
--   - Horas de horario: time sin zona ("abrimos a las 8" es local a la sede)
--   - Dinero: entero en pesos colombianos, sin decimales
--   - Los estados son varchar con CHECK, no tipos enum de Postgres:
--     agregar un estado nuevo es una migracion de una linea, no un ALTER TYPE
--
-- ESTA MIGRACION NO SE EDITA una vez aplicada en develop (ADR-007).
-- Cualquier correccion va en una migracion nueva.
-- =====================================================================

-- Necesaria para la restriccion EXCLUDE contra doble reserva (ADR-003).
-- Permite combinar igualdad (staff_id) con solapamiento de rangos en un
-- mismo indice GiST.
CREATE EXTENSION IF NOT EXISTS btree_gist;


-- =====================================================================
-- NEGOCIO Y ESTRUCTURA
-- =====================================================================

CREATE TABLE business (
    id              uuid PRIMARY KEY,
    name            varchar(120) NOT NULL,
    slug            varchar(80)  NOT NULL,
    type            varchar(40)  NOT NULL DEFAULT 'BARBERSHOP',
    referral_code   varchar(20)  NOT NULL,
    referred_by_id  uuid         REFERENCES business (id),

    -- Tema de la pagina publica (Etapa 4).
    -- Las 5 paletas viven en codigo, no aqui: la base solo guarda cual eligio
    -- el negocio, para poder afinar una paleta sin migrar datos.
    -- theme_colors solo se llena cuando el preset es 'custom'.
    theme_preset    varchar(40)  NOT NULL DEFAULT 'default',
    theme_colors    jsonb,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT business_slug_unique          UNIQUE (slug),
    CONSTRAINT business_referral_code_unique UNIQUE (referral_code),
    -- Un negocio no puede haberse referido a si mismo
    CONSTRAINT business_referral_not_self    CHECK (referred_by_id IS NULL OR referred_by_id <> id),
    -- Mantiene la invariante documentada: colores a mano solo en 'custom'
    CONSTRAINT business_theme_colors_only_custom CHECK (
        (theme_preset =  'custom' AND theme_colors IS NOT NULL) OR
        (theme_preset <> 'custom' AND theme_colors IS NULL)
    )
);

CREATE TABLE location (
    id           uuid PRIMARY KEY,
    business_id  uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    name         varchar(120) NOT NULL,
    address      varchar(200),
    city         varchar(80),
    is_active    boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE staff (
    id           uuid PRIMARY KEY,
    location_id  uuid NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    name         varchar(120) NOT NULL,
    is_active    boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE service (
    id            uuid PRIMARY KEY,
    location_id   uuid NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    name          varchar(120) NOT NULL,
    price         integer NOT NULL,
    duration_min  integer NOT NULL,
    is_active     boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT service_price_non_negative CHECK (price >= 0),
    -- La agenda se dibuja en bloques de 30 minutos: una duracion que no sea
    -- multiplo rompe el motor de disponibilidad
    CONSTRAINT service_duration_half_hour CHECK (duration_min > 0 AND duration_min % 30 = 0)
);

-- Que barbero sabe hacer que servicio
CREATE TABLE skill (
    id          uuid PRIMARY KEY,
    staff_id    uuid NOT NULL REFERENCES staff (id)   ON DELETE CASCADE,
    service_id  uuid NOT NULL REFERENCES service (id) ON DELETE CASCADE,

    CONSTRAINT skill_staff_service_unique UNIQUE (staff_id, service_id)
);


-- =====================================================================
-- USUARIOS
-- =====================================================================

-- Se llama app_user y no user porque USER es palabra reservada en SQL:
-- una tabla llamada user obliga a escribir "user" entrecomillado en cada
-- consulta nativa, y basta olvidarlo una vez para tener un error raro.
CREATE TABLE app_user (
    id             uuid PRIMARY KEY,
    business_id    uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    -- Solo se llena cuando role = 'STAFF': conecta al barbero con su login
    staff_id       uuid REFERENCES staff (id) ON DELETE SET NULL,
    email          varchar(180) NOT NULL,
    password_hash  varchar(120) NOT NULL,
    role           varchar(20)  NOT NULL,
    is_active      boolean NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT app_user_email_unique UNIQUE (email),
    CONSTRAINT app_user_role_valid   CHECK (role IN ('OWNER', 'STAFF', 'SUPER_ADMIN')),
    -- Un STAFF sin staff_id no puede ver su propia agenda; un OWNER con
    -- staff_id confunde el modelo de permisos
    CONSTRAINT app_user_staff_only_for_staff_role CHECK (
        (role =  'STAFF' AND staff_id IS NOT NULL) OR
        (role <> 'STAFF' AND staff_id IS NULL)
    )
);


-- =====================================================================
-- HORARIOS Y BLOQUEOS
-- =====================================================================

CREATE TABLE schedule (
    id           uuid PRIMARY KEY,
    location_id  uuid NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    -- Nulo = horario general de la sede.
    -- Con valor = horario propio de ese barbero, que pisa al de la sede.
    staff_id     uuid REFERENCES staff (id) ON DELETE CASCADE,
    day_of_week  smallint NOT NULL,
    open_time    time NOT NULL,
    close_time   time NOT NULL,

    -- 0 = domingo, 6 = sabado (ISO de java.time.DayOfWeek desplazado)
    CONSTRAINT schedule_day_of_week_valid CHECK (day_of_week BETWEEN 0 AND 6),
    CONSTRAINT schedule_open_before_close CHECK (open_time < close_time)
);

-- Vacaciones, permisos, una tarde libre: lo puntual que no cabe en un
-- horario semanal recurrente
CREATE TABLE block (
    id        uuid PRIMARY KEY,
    staff_id  uuid NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    start_at  timestamptz NOT NULL,
    end_at    timestamptz NOT NULL,
    reason    varchar(200),

    CONSTRAINT block_start_before_end CHECK (start_at < end_at)
);


-- =====================================================================
-- FIDELIZACION Y CLIENTES
-- =====================================================================

CREATE TABLE loyalty (
    id               uuid PRIMARY KEY,
    business_id      uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    stamps_required  integer NOT NULL,
    reward           varchar(200) NOT NULL,
    is_default       boolean NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT loyalty_stamps_positive CHECK (stamps_required > 0)
);

-- Un solo plan por defecto por negocio. Indice parcial: solo vigila las
-- filas con is_default = true.
CREATE UNIQUE INDEX loyalty_one_default_per_business
    ON loyalty (business_id) WHERE is_default;

-- El cliente final no tiene cuenta: se identifica por telefono dentro del
-- negocio y gestiona su cita con el enlace privado (ADR-006)
CREATE TABLE client (
    id                    uuid PRIMARY KEY,
    business_id           uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    loyalty_id            uuid REFERENCES loyalty (id) ON DELETE SET NULL,
    name                  varchar(120) NOT NULL,
    phone                 varchar(20)  NOT NULL,
    email                 varchar(180),
    attended_count        integer NOT NULL DEFAULT 0,
    consecutive_no_shows  integer NOT NULL DEFAULT 0,
    stamps_count          integer NOT NULL DEFAULT 0,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT client_phone_per_business UNIQUE (business_id, phone),
    CONSTRAINT client_counters_non_negative CHECK (
        attended_count >= 0 AND consecutive_no_shows >= 0 AND stamps_count >= 0
    )
);


-- =====================================================================
-- CITAS
-- =====================================================================

CREATE TABLE appointment (
    id            uuid PRIMARY KEY,
    location_id   uuid NOT NULL REFERENCES location (id) ON DELETE CASCADE,
    staff_id      uuid NOT NULL REFERENCES staff (id)    ON DELETE RESTRICT,
    service_id    uuid NOT NULL REFERENCES service (id)  ON DELETE RESTRICT,
    client_id     uuid NOT NULL REFERENCES client (id)   ON DELETE RESTRICT,
    scheduled_at  timestamptz NOT NULL,

    -- Copia de SERVICE al momento de agendar (ADR-010).
    -- Si manana sube el precio del corte, esta cita conserva el que se
    -- pacto con el cliente y los informes historicos siguen cuadrando.
    duration_min  integer NOT NULL,
    price         integer NOT NULL,

    -- Fin de la cita, materializado como columna.
    --
    -- No es dato redundante por descuido: la restriccion EXCLUDE de mas
    -- abajo necesita un rango de tiempo, y Postgres exige que toda
    -- expresion dentro de un indice sea IMMUTABLE. Sumar un intervalo a un
    -- timestamptz NO lo es (el resultado depende de la zona horaria de la
    -- sesion), asi que el calculo no puede vivir dentro del indice.
    --
    -- Lo mantiene sincronizado un trigger, no la aplicacion: asi la
    -- garantia no depende de que nadie olvide actualizarlo.
    ends_at       timestamptz NOT NULL,

    status        varchar(20) NOT NULL DEFAULT 'PENDING',
    -- Unica credencial del cliente para gestionar su cita: debe ser
    -- aleatorio y largo, nunca secuencial (ADR-006)
    manage_token  varchar(64) NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT appointment_token_unique UNIQUE (manage_token),
    CONSTRAINT appointment_status_valid CHECK (
        status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')
    ),
    CONSTRAINT appointment_price_non_negative CHECK (price >= 0),
    CONSTRAINT appointment_duration_positive  CHECK (duration_min > 0),
    CONSTRAINT appointment_ends_after_start   CHECK (ends_at > scheduled_at)
);

-- ---------------------------------------------------------------------
-- Mantiene ends_at coherente con scheduled_at + duration_min.
--
-- Va en un trigger y no en la aplicacion a proposito: si el calculo
-- viviera en el codigo, bastaria un servicio nuevo, un script de
-- correccion o un INSERT manual para dejar ends_at desalineado, y con el
-- la restriccion de no solapamiento dejaria de proteger.
-- ---------------------------------------------------------------------
CREATE FUNCTION appointment_sync_ends_at() RETURNS trigger AS $$
BEGIN
    NEW.ends_at := NEW.scheduled_at + make_interval(mins => NEW.duration_min);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER appointment_ends_at_before_write
    BEFORE INSERT OR UPDATE OF scheduled_at, duration_min ON appointment
    FOR EACH ROW EXECUTE FUNCTION appointment_sync_ends_at();

-- ---------------------------------------------------------------------
-- LA restriccion mas importante del sistema (ADR-003).
--
-- Impide que un mismo barbero tenga dos citas solapadas. No es una
-- validacion mas: es la unica defensa real contra la doble reserva.
-- Validar en codigo no basta, porque dos transacciones concurrentes ven
-- el horario libre hasta que una hace commit.
--
-- El WHERE excluye las citas canceladas y las no asistidas: ese horario
-- vuelve a estar disponible.
--
-- NO ELIMINAR NI DEBILITAR. Si un test falla contra esta restriccion,
-- el error esta en el codigo, no aqui.
-- ---------------------------------------------------------------------
ALTER TABLE appointment ADD CONSTRAINT appointment_no_overlap
    EXCLUDE USING gist (
        staff_id WITH =,
        tstzrange(scheduled_at, ends_at) WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED'));


-- =====================================================================
-- SUSCRIPCION DEL NEGOCIO A GUARDAO
-- =====================================================================

CREATE TABLE subscription (
    id                uuid PRIMARY KEY,
    business_id       uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    plan              varchar(40) NOT NULL,
    status            varchar(20) NOT NULL DEFAULT 'TRIAL',
    current_period_end timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT subscription_business_unique UNIQUE (business_id),
    CONSTRAINT subscription_status_valid CHECK (
        status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELLED')
    )
);


-- =====================================================================
-- TIENDA (Etapa 7, tablas creadas desde ya para no migrar despues)
-- =====================================================================

CREATE TABLE product (
    id           uuid PRIMARY KEY,
    business_id  uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    name         varchar(120) NOT NULL,
    price        integer NOT NULL,
    image_url    varchar(500),
    -- Nulo = el negocio no controla inventario (stock ilimitado)
    stock        integer,
    is_active    boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT product_price_non_negative CHECK (price >= 0),
    -- El CHECK se cumple cuando stock es nulo: solo vigila los que si se controlan
    CONSTRAINT product_stock_non_negative CHECK (stock >= 0)
);

-- Se llama "orders" en plural porque ORDER es palabra reservada en SQL
CREATE TABLE orders (
    id           uuid PRIMARY KEY,
    business_id  uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    client_id    uuid NOT NULL REFERENCES client (id)   ON DELETE RESTRICT,
    total        integer NOT NULL,
    status       varchar(20) NOT NULL DEFAULT 'PENDING',
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT orders_total_non_negative CHECK (total >= 0),
    CONSTRAINT orders_status_valid CHECK (
        status IN ('PENDING', 'PAID', 'DELIVERED', 'CANCELLED')
    )
);

CREATE TABLE item (
    id          uuid PRIMARY KEY,
    order_id    uuid NOT NULL REFERENCES orders (id)   ON DELETE CASCADE,
    product_id  uuid NOT NULL REFERENCES product (id)  ON DELETE RESTRICT,
    quantity    integer NOT NULL,
    -- Snapshot del precio, misma razon que en appointment (ADR-010)
    unit_price  integer NOT NULL,

    CONSTRAINT item_quantity_positive    CHECK (quantity > 0),
    CONSTRAINT item_unit_price_non_negative CHECK (unit_price >= 0)
);


-- =====================================================================
-- PAGOS
-- =====================================================================

CREATE TABLE payment (
    id              uuid PRIMARY KEY,
    appointment_id  uuid REFERENCES appointment (id)  ON DELETE RESTRICT,
    subscription_id uuid REFERENCES subscription (id) ON DELETE RESTRICT,
    order_id        uuid REFERENCES orders (id)       ON DELETE RESTRICT,
    type            varchar(20) NOT NULL,
    method          varchar(20) NOT NULL,
    amount          integer NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'PENDING',
    payment_link    varchar(500),
    -- Identificador del proveedor: sostiene la idempotencia del webhook
    external_id     varchar(120),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT payment_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT payment_type_valid   CHECK (type IN ('APPOINTMENT', 'SUBSCRIPTION', 'ORDER')),
    CONSTRAINT payment_method_valid CHECK (method IN ('CASH', 'ONLINE')),
    CONSTRAINT payment_status_valid CHECK (
        status IN ('PENDING', 'APPROVED', 'DECLINED', 'REFUNDED', 'ERROR')
    ),
    -- Un pago cobra exactamente una cosa: cita, suscripcion u orden.
    -- Sin esto es posible una fila que cobra dos veces o ninguna.
    CONSTRAINT payment_exactly_one_target CHECK (
        num_nonnulls(appointment_id, subscription_id, order_id) = 1
    )
);

-- Evita procesar dos veces el mismo evento de la pasarela.
-- Indice parcial: los pagos en efectivo no tienen external_id.
CREATE UNIQUE INDEX payment_external_id_unique
    ON payment (external_id) WHERE external_id IS NOT NULL;

-- Credenciales de la pasarela de cada negocio, cifradas en reposo (ADR-005).
-- Guardao no las descifra para nada que no sea firmar una peticion.
CREATE TABLE gateway (
    id                     uuid PRIMARY KEY,
    business_id            uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    provider               varchar(40) NOT NULL DEFAULT 'WOMPI',
    encrypted_credentials  text NOT NULL,
    is_active              boolean NOT NULL DEFAULT true,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT gateway_business_provider_unique UNIQUE (business_id, provider)
);


-- =====================================================================
-- PRESENCIA PUBLICA (Etapa 7)
-- =====================================================================

CREATE TABLE social (
    id              uuid PRIMARY KEY,
    business_id     uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    platform        varchar(40) NOT NULL,
    username        varchar(120),
    access_token    text,
    last_synced_at  timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT social_business_platform_unique UNIQUE (business_id, platform)
);

CREATE TABLE gallery (
    id           uuid PRIMARY KEY,
    business_id  uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    image_url    varchar(500) NOT NULL,
    position     integer NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now()
);


-- =====================================================================
-- NOTIFICACIONES
-- =====================================================================

CREATE TABLE notification (
    id                   uuid PRIMARY KEY,
    appointment_id       uuid NOT NULL REFERENCES appointment (id) ON DELETE CASCADE,
    channel              varchar(20) NOT NULL,
    type                 varchar(40) NOT NULL,
    status               varchar(20) NOT NULL DEFAULT 'PENDING',
    -- Lo devuelve el proveedor. Es lo que permite responder al cliente
    -- cuando contesta el recordatorio, sin interpretar su texto (ADR-009).
    provider_message_id  varchar(120),
    error_message        varchar(500),
    sent_at              timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT notification_channel_valid CHECK (channel IN ('WHATSAPP', 'EMAIL')),
    CONSTRAINT notification_status_valid  CHECK (
        status IN ('PENDING', 'SENT', 'FAILED')
    )
);

CREATE INDEX idx_notification_provider_message
    ON notification (provider_message_id) WHERE provider_message_id IS NOT NULL;


-- =====================================================================
-- INDICES (Tech Spec 3.3)
-- =====================================================================

-- El motor de disponibilidad es la consulta mas frecuente y la mas
-- sensible a latencia: este par de indices es lo que la sostiene.
CREATE INDEX idx_appointment_location_date ON appointment (location_id, scheduled_at);
CREATE INDEX idx_appointment_staff_date    ON appointment (staff_id, scheduled_at);
CREATE INDEX idx_schedule_location_day     ON schedule (location_id, day_of_week);
CREATE INDEX idx_block_staff_range         ON block (staff_id, start_at, end_at);
CREATE INDEX idx_client_business_phone     ON client (business_id, phone);

-- Filtrado multi-tenant: toda consulta del dashboard pasa por business_id
-- (ADR-004), asi que las claves foraneas hacia business necesitan indice.
CREATE INDEX idx_location_business  ON location (business_id);
CREATE INDEX idx_client_business    ON client (business_id);
CREATE INDEX idx_product_business   ON product (business_id);
CREATE INDEX idx_app_user_business  ON app_user (business_id);
CREATE INDEX idx_orders_business    ON orders (business_id);
CREATE INDEX idx_staff_location     ON staff (location_id);
CREATE INDEX idx_service_location   ON service (location_id);
CREATE INDEX idx_appointment_client ON appointment (client_id);
CREATE INDEX idx_notification_appointment ON notification (appointment_id);
