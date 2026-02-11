-- Create webhooks table
CREATE TABLE webhooks (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(255) NOT NULL,
    slug               VARCHAR(255) NOT NULL UNIQUE,
    description        TEXT,
    methods            VARCHAR(50) NOT NULL DEFAULT 'GET,POST',
    is_active          BOOLEAN NOT NULL DEFAULT true,
    debug_mode         BOOLEAN NOT NULL DEFAULT true,
    proxy_url          TEXT,
    proxy_headers      JSONB,
    request_template   TEXT,
    response_template  TEXT,
    max_log_count      INTEGER NOT NULL DEFAULT 100,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create index on slug for faster lookups
CREATE INDEX idx_webhooks_slug ON webhooks(slug);

-- Create index on is_active for filtering
CREATE INDEX idx_webhooks_is_active ON webhooks(is_active);
