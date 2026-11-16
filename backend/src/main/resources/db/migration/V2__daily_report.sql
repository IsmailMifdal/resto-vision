-- Rapport quotidien : rédigé une fois par jour terminé, puis relu tel quel.
CREATE TABLE IF NOT EXISTS rv_daily_report (
    id            uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES rv_restaurant (id),
    report_date   date NOT NULL,
    text          varchar(4000) NOT NULL,
    source        varchar(20) NOT NULL CHECK (source IN ('AI', 'TEMPLATE')),
    generated_at  timestamp(6) with time zone NOT NULL,
    UNIQUE (restaurant_id, report_date)
);
