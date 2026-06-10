-- Create the market_enriched table
CREATE TABLE IF NOT EXISTS market_enriched (
    time        TIMESTAMPTZ       NOT NULL,
    symbol      TEXT              NOT NULL,
    price       DECIMAL(18,8)     NULL, -- using close price as default reference
    open        DECIMAL(18,8)     NULL,
    high        DECIMAL(18,8)     NULL,
    low         DECIMAL(18,8)     NULL,
    close       DECIMAL(18,8)     NULL,
    volume      BIGINT            NULL,
    start_time  TIMESTAMPTZ       NULL,
    end_time    TIMESTAMPTZ       NULL
);

-- Convert to hypertable
SELECT create_hypertable('market_enriched', 'time', if_not_exists => TRUE);

-- Index for fast "latest price" lookup by symbol
CREATE INDEX IF NOT EXISTS market_enriched_symbol_time_idx ON market_enriched (symbol, time DESC);
