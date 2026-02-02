# Plan: Replace Local RocksDB with TimescaleDB for Market Enriched Data

## Objective
Replace the usage of local RocksDB files in `risk-calculator-service` (and potentially other components) with a centralized TimescaleDB instance for storing and retrieving "Market Enriched" data. This moves the system from a localized state store pattern to a shared persistence pattern for market data reference.

## Current State
- **Data Source**: `risk-stream-processor` produces `EnrichedTick` data to the `market.enriched` Kafka topic.
- **Consumer**: `risk-calculator-service` consumes `market.enriched` into a `GlobalKTable`, which materializes the latest price for every symbol into a **local RocksDB** instance (`market-store`) on every pod.
- **Usage**: The `PortfolioPricer` in `risk-calculator-service` queries this local RocksDB to enrich portfolio positions with real-time prices.
- **Infrastructure**: Only a basic TimescaleDB Helm chart exists; no schema for enriched data.

## Desired State
- **Persistence Layer**: A centralized **TimescaleDB** instance stores all historical and real-time enriched market data (`market_enriched` hypertable).
- **Ingestion**: The `market-ingestion-service` acts as a dedicated worker to consume `market.enriched` and batch-write to TimescaleDB.
- **Access Pattern**: The `risk-calculator-service` no longer maintains heavy local state. Instead, it queries TimescaleDB (with a short-lived local cache like Caffeine) to fetch reference prices.
- **Benefits**:
    - **Stateless Calculation Pods**: Faster startup/restart times for `risk-calculator-service` (no restoring RocksDB from changelogs).
    - **Single Source of Truth**: Unified database for both real-time pricing and historical analytics.
    - **Scalability**: Database scales independently of the calculation logic.

## Proposed Changes

### Phase 1: Database Infrastructure
1.  **Schema Definition**:
    - Update `infrastructure/timescaledb/schema.sql` to include a table `market_enriched` optimized for time-series data.
    - Enable hypertable functionality for efficient time-partitioning.

### Phase 2: Data Ingestion (Kafka -> TimescaleDB)
We need a component to persist data from the `market.enriched` topic to TimescaleDB.
1.  **Enhance `market-ingestion-service`** (or create a new worker):
    - Add dependencies for PostgreSQL/JDBC.
    - Implement a Kafka Consumer for the `market.enriched` topic.
    - Implement a batch writer to insert `EnrichedTick` records into the `market_enriched` table in TimescaleDB.

### Phase 3: Refactoring Risk Calculator (The Shift to Stateless)
This is the critical architectural shift from "Stateful Streaming" to "Stateless Compute".
1.  **Remove Aggregation (`HistoricalWindow`)**:
    - **Delete**: The `.aggregate()` step in `RiskCalculatorTopology`.
    - **Delete**: The `HistoricalWindow` class.
    - **Reasoning**: We no longer accumulate state in RocksDB.
2.  **Remove Global Table (`market-store`)**:
    - **Delete**: The `GlobalKTable` and `PortfolioPricer` lookup.
3.  **Implement On-Demand Lookup**:
    - Create `TimescaleHistoryService`.
    - Logic: On receiving `RiskPortfolioState`, query TimescaleDB for the last N records (e.g., 500-1000) for the relevant symbols.
    - Calculate VaR using this fresh history.
4.  **Performance Check**: 
    - Ensure we use a cached connection pool (HikariCP).
    - Consider caching frequent queries if simulation frequency is high.

## Detailed Steps

### 1. Database Schema
Update `infrastructure/timescaledb/schema.sql`:
```sql
-- "market_enriched" matches the EnrichedTick data from stream processor
CREATE TABLE IF NOT EXISTS market_enriched (
    time        TIMESTAMPTZ       NOT NULL,
    symbol      TEXT              NOT NULL,
    price       DECIMAL(18,8)     NULL, -- Maps to close price
    open        DECIMAL(18,8)     NULL,
    high        DECIMAL(18,8)     NULL,
    low         DECIMAL(18,8)     NULL,
    close       DECIMAL(18,8)     NULL,
    volume      BIGINT            NULL,
    start_time  TIMESTAMPTZ       NULL,
    end_time    TIMESTAMPTZ       NULL
);
SELECT create_hypertable('market_enriched', 'time', if_not_exists => TRUE);
-- Compound index for fast "Get last N ticks for symbol"
CREATE INDEX IF NOT EXISTS market_enriched_symbol_time_idx ON market_enriched (symbol, time DESC);
```

### 2. Ingestion Service
Modify `apps/market-ingestion-service`:
- This service will act as the "Archiver".
- Add a consumer for `market.enriched` that batched inserts into `market_enriched`.

### 3. Risk Calculator
Modify `apps/risk-calculator-service/src/main/java/com/var/risk/calculator/topology/RiskCalculatorTopology.java`:
- Simplify topology to: `Source -> MapValues(Fetch History & Calc VaR) -> Sink`.

