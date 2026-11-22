-- Position du restaurant, pour la météo de la prévision. Vide : pas de météo.
ALTER TABLE rv_restaurant ADD COLUMN IF NOT EXISTS latitude double precision;
ALTER TABLE rv_restaurant ADD COLUMN IF NOT EXISTS longitude double precision;
