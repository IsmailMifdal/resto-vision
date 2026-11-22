-- Fournisseur d'un ingrédient, pour regrouper le bon de commande.
ALTER TABLE rv_ingredient ADD COLUMN IF NOT EXISTS supplier varchar(120);
