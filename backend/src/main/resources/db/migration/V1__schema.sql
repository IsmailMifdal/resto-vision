-- Schéma de départ. Écrit pour pouvoir passer aussi sur une base créée avant Flyway
-- (par Hibernate) : rien n'est recréé si la table existe déjà.

CREATE TABLE IF NOT EXISTS rv_restaurant (
    id         uuid PRIMARY KEY,
    code       varchar(255) NOT NULL UNIQUE,
    name       varchar(255) NOT NULL,
    group_code varchar(255)
);

CREATE TABLE IF NOT EXISTS rv_user (
    id            uuid PRIMARY KEY,
    email         varchar(255) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    role          varchar(255) NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'STAFF')),
    restaurant_id uuid NOT NULL REFERENCES rv_restaurant (id)
);

CREATE TABLE IF NOT EXISTS rv_zone (
    id                     uuid PRIMARY KEY,
    restaurant_id          uuid NOT NULL REFERENCES rv_restaurant (id),
    code                   varchar(255) NOT NULL,
    type                   varchar(255) NOT NULL CHECK (type IN ('TABLE', 'QUEUE')),
    state                  varchar(255) CHECK (state IN ('EMPTY', 'WAITING', 'SERVED', 'TO_CLEAR')),
    state_since            timestamp(6) with time zone,
    persons                integer NOT NULL,
    dishes                 integer NOT NULL,
    estimated_wait_seconds integer,
    updated_at             timestamp(6) with time zone,
    polygon                varchar(4000),
    UNIQUE (restaurant_id, code)
);

CREATE TABLE IF NOT EXISTS rv_zone_event (
    id             uuid PRIMARY KEY,
    zone_id        uuid NOT NULL REFERENCES rv_zone (id),
    state          varchar(255) CHECK (state IN ('EMPTY', 'WAITING', 'SERVED', 'TO_CLEAR')),
    previous_state varchar(255) CHECK (previous_state IN ('EMPTY', 'WAITING', 'SERVED', 'TO_CLEAR')),
    persons        integer NOT NULL,
    dishes         integer NOT NULL,
    occurred_at    timestamp(6) with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS rv_alert_rule (
    id            uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES rv_restaurant (id),
    type          varchar(255) NOT NULL CHECK (type IN ('UNSERVED', 'TO_CLEAR', 'QUEUE_LENGTH')),
    threshold     integer NOT NULL,
    enabled       boolean NOT NULL
);

CREATE TABLE IF NOT EXISTS rv_alert (
    id              uuid PRIMARY KEY,
    zone_id         uuid NOT NULL REFERENCES rv_zone (id),
    type            varchar(255) NOT NULL CHECK (type IN ('UNSERVED', 'TO_CLEAR', 'QUEUE_LENGTH')),
    message         varchar(255) NOT NULL,
    created_at      timestamp(6) with time zone NOT NULL,
    acknowledged_at timestamp(6) with time zone,
    resolved_at     timestamp(6) with time zone
);

-- L'historique est lu par restaurant et par période : sans index, chaque écran « Service » relit toute la table.
CREATE INDEX IF NOT EXISTS rv_zone_event_zone_time ON rv_zone_event (zone_id, occurred_at);
CREATE INDEX IF NOT EXISTS rv_alert_zone ON rv_alert (zone_id);
