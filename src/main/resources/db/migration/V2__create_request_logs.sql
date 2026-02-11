-- Create request_logs table
CREATE TABLE request_logs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id         UUID NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
    received_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    method             VARCHAR(10) NOT NULL,
    url                TEXT NOT NULL,
    query_params       JSONB,
    headers            JSONB,
    body               TEXT,
    content_type       VARCHAR(255),
    source_ip          VARCHAR(45),
    response_status    INTEGER,
    proxy_response     TEXT,
    proxy_duration_ms  BIGINT
);

-- Create index on webhook_id for faster lookups
CREATE INDEX idx_logs_webhook_id ON request_logs(webhook_id);

-- Create index on received_at for sorting and filtering
CREATE INDEX idx_logs_received_at ON request_logs(received_at DESC);

-- Create composite index for common queries
CREATE INDEX idx_logs_webhook_received ON request_logs(webhook_id, received_at DESC);
