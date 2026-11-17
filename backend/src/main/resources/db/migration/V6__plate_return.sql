-- Anti-gaspillage : nourriture encore visible sur une table au départ des clients.
CREATE TABLE IF NOT EXISTS rv_plate_return (
    id          uuid PRIMARY KEY,
    zone_id     uuid NOT NULL REFERENCES rv_zone (id),
    occurred_at timestamp(6) with time zone NOT NULL,
    served_food integer NOT NULL,
    left_food   integer NOT NULL
);
CREATE INDEX IF NOT EXISTS rv_plate_return_zone_time ON rv_plate_return (zone_id, occurred_at);
