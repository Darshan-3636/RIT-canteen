CREATE TABLE sellers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_main_canteen BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT sellers_id_stable CHECK (id ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
    CONSTRAINT sellers_name_present CHECK (length(btrim(name)) BETWEEN 1 AND 200)
);

CREATE UNIQUE INDEX sellers_one_main_canteen_idx ON sellers (is_main_canteen) WHERE is_main_canteen;
CREATE INDEX sellers_active_idx ON sellers (active);
