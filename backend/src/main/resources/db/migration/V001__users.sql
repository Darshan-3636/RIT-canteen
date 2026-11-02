CREATE TABLE users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name TEXT NOT NULL,
    affiliation TEXT,
    department TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    is_platform_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_email_normalized CHECK (email = lower(btrim(email)) AND length(email) BETWEEN 3 AND 320),
    CONSTRAINT users_password_hash_present CHECK (length(password_hash) > 20),
    CONSTRAINT users_full_name_present CHECK (length(btrim(full_name)) BETWEEN 1 AND 200),
    CONSTRAINT users_affiliation_valid CHECK (affiliation IS NULL OR affiliation IN ('STUDENT', 'FACULTY')),
    CONSTRAINT users_status_valid CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT users_department_present CHECK (department IS NULL OR length(btrim(department)) BETWEEN 1 AND 120)
);

CREATE INDEX users_status_idx ON users (status);
