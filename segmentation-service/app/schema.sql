CREATE SCHEMA IF NOT EXISTS market_intelligence;
CREATE TABLE IF NOT EXISTS market_intelligence.profiles (
    owner_id varchar(36) PRIMARY KEY REFERENCES public.farmers(id) ON DELETE CASCADE,
    data jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    consent_updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS market_intelligence.records (
    id uuid PRIMARY KEY,
    owner_id varchar(36) NOT NULL REFERENCES public.farmers(id) ON DELETE CASCADE,
    data jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS mi_records_owner ON market_intelligence.records(owner_id);
CREATE TABLE IF NOT EXISTS market_intelligence.searches (
    id bigserial PRIMARY KEY,
    owner_id varchar(36) NOT NULL REFERENCES public.farmers(id) ON DELETE CASCADE,
    data jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS market_intelligence.prices (
    crop varchar(30) NOT NULL,
    market varchar(100) NOT NULL,
    observed_on date NOT NULL,
    data jsonb NOT NULL,
    PRIMARY KEY (crop, market, observed_on)
);
CREATE TABLE IF NOT EXISTS market_intelligence.impressions (
    id uuid PRIMARY KEY,
    owner_id varchar(36) NOT NULL REFERENCES public.farmers(id) ON DELETE CASCADE,
    data jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS market_intelligence.feedback (
    impression_id uuid NOT NULL REFERENCES market_intelligence.impressions(id) ON DELETE CASCADE,
    record_id uuid NOT NULL,
    action varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (impression_id, record_id, action)
);
CREATE TABLE IF NOT EXISTS market_intelligence.order_outcomes (
    event_id uuid PRIMARY KEY,
    order_id uuid NOT NULL UNIQUE,
    farmer_id varchar(36) NOT NULL REFERENCES public.farmers(id) ON DELETE CASCADE,
    buyer_id varchar(36) NOT NULL REFERENCES public.farmers(id) ON DELETE CASCADE,
    record_id uuid NOT NULL REFERENCES market_intelligence.records(id) ON DELETE CASCADE,
    impression_id uuid REFERENCES market_intelligence.impressions(id) ON DELETE SET NULL,
    data jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (farmer_id <> buyer_id),
    CHECK (data->>'status' = 'completed')
);
CREATE INDEX IF NOT EXISTS mi_outcomes_farmer ON market_intelligence.order_outcomes(farmer_id);
CREATE INDEX IF NOT EXISTS mi_outcomes_buyer ON market_intelligence.order_outcomes(buyer_id);
CREATE INDEX IF NOT EXISTS mi_outcomes_impression ON market_intelligence.order_outcomes(impression_id);
CREATE TABLE IF NOT EXISTS market_intelligence.snapshots (
    snapshot_day date PRIMARY KEY,
    data jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS market_intelligence.jobs (
    name varchar(100) PRIMARY KEY,
    completed_at timestamptz NOT NULL DEFAULT now(),
    data jsonb NOT NULL
);
