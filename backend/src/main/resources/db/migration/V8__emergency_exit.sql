-- Sortie de secours : un nouveau type de zone, son état, et une règle d'alerte par restaurant.
ALTER TABLE rv_zone ADD COLUMN IF NOT EXISTS blocked boolean;
ALTER TABLE rv_zone ADD COLUMN IF NOT EXISTS obstacles integer;
ALTER TABLE rv_zone DROP CONSTRAINT IF EXISTS rv_zone_type_check;
ALTER TABLE rv_zone ADD CONSTRAINT rv_zone_type_check CHECK (type IN ('TABLE', 'QUEUE', 'EXIT'));
ALTER TABLE rv_alert DROP CONSTRAINT IF EXISTS rv_alert_type_check;
ALTER TABLE rv_alert ADD CONSTRAINT rv_alert_type_check CHECK (type IN ('UNSERVED', 'TO_CLEAR', 'QUEUE_LENGTH', 'EXIT_BLOCKED'));
ALTER TABLE rv_alert_rule DROP CONSTRAINT IF EXISTS rv_alert_rule_type_check;
ALTER TABLE rv_alert_rule ADD CONSTRAINT rv_alert_rule_type_check CHECK (type IN ('UNSERVED', 'TO_CLEAR', 'QUEUE_LENGTH', 'EXIT_BLOCKED'));
INSERT INTO rv_alert_rule (id, restaurant_id, type, threshold, enabled)
SELECT gen_random_uuid(), r.id, 'EXIT_BLOCKED', 60, true FROM rv_restaurant r
WHERE NOT EXISTS (SELECT 1 FROM rv_alert_rule a WHERE a.restaurant_id = r.id AND a.type = 'EXIT_BLOCKED');
