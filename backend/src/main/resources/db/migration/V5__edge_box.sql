-- Boîtiers : dernier signal de vie de chacun, pour savoir s'il tourne.
CREATE TABLE IF NOT EXISTS rv_edge_box (
    id             uuid PRIMARY KEY,
    restaurant_id  uuid NOT NULL REFERENCES rv_restaurant (id),
    box_id         varchar(120) NOT NULL,
    source         varchar(40),
    version        varchar(40),
    fps            double precision,
    buffered       integer NOT NULL,
    uptime_seconds bigint NOT NULL,
    last_seen      timestamp(6) with time zone NOT NULL,
    online         boolean NOT NULL,
    UNIQUE (restaurant_id, box_id)
);
