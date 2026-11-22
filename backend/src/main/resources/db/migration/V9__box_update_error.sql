-- Dernier échec de mise à jour signalé par un boîtier (signature invalide, paquet illisible...).
ALTER TABLE rv_edge_box ADD COLUMN IF NOT EXISTS update_error varchar(200);
