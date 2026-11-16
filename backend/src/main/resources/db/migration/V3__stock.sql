-- Stock : ingrédients, recettes par plat, ventes importées de la caisse.
CREATE TABLE IF NOT EXISTS rv_ingredient (
    id            uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES rv_restaurant (id),
    name          varchar(120) NOT NULL,
    unit          varchar(20) NOT NULL,
    quantity      numeric(12, 3) NOT NULL,
    threshold     numeric(12, 3) NOT NULL,
    updated_at    timestamp(6) with time zone NOT NULL,
    UNIQUE (restaurant_id, name)
);

CREATE TABLE IF NOT EXISTS rv_dish (
    id            uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES rv_restaurant (id),
    name          varchar(120) NOT NULL,
    UNIQUE (restaurant_id, name)
);

CREATE TABLE IF NOT EXISTS rv_recipe_line (
    id            uuid PRIMARY KEY,
    dish_id       uuid NOT NULL REFERENCES rv_dish (id),
    ingredient_id uuid NOT NULL REFERENCES rv_ingredient (id),
    quantity      numeric(12, 3) NOT NULL
);

-- Un fichier de ventes n'est compté qu'une fois : son empreinte est gardée.
CREATE TABLE IF NOT EXISTS rv_sales_import (
    id            uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES rv_restaurant (id),
    file_hash     varchar(64) NOT NULL,
    file_name     varchar(255) NOT NULL,
    imported_at   timestamp(6) with time zone NOT NULL,
    line_count    integer NOT NULL,
    UNIQUE (restaurant_id, file_hash)
);

CREATE TABLE IF NOT EXISTS rv_sale (
    id              uuid PRIMARY KEY,
    sales_import_id uuid NOT NULL REFERENCES rv_sales_import (id),
    sale_date       date NOT NULL,
    dish_name       varchar(120) NOT NULL,
    quantity        numeric(12, 3) NOT NULL,
    matched         boolean NOT NULL
);
