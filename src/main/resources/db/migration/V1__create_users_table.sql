CREATE TABLE users (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100)             NOT NULL,
    email       VARCHAR(255)             NOT NULL,
    password    VARCHAR(100)             NOT NULL,
    role        VARCHAR(20)              NOT NULL,
    enabled     BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- Índice único: garantiza la unicidad del email y acelera el login
CREATE UNIQUE INDEX ux_users_email ON users (email);
