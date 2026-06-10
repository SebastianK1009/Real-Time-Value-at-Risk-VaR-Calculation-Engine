# Risk Calculator Service

## Overview
The **Risk Calculator Service** is the computational heart of the Real-Time VaR system. It is a specialized consumer of the enriched data streams produced by the upstream processors. Its mission is to perform complex financial modeling in real-time to quantify the potential financial loss of a portfolio.

### The Problem it Solves
After the data is cleaned and aggregated:
*   **The Question**: "Given that I own 50 shares of AAPL and the market is moving this way, what is the maximum amount I could lose in the next hour with 99% confidence?"
*   **The Complexity**: This isn't a simple sum. It requires simulating thousands of possible future scenarios (Monte Carlo) or analyzing historical patterns (Historical Simulation) against every single asset in a portfolio, continuously.

The Risk Calculator Service solves this by maintaining a **Sliding Window of History** and triggering heavy computational models on every significant state change.

---

## Non-Technical Explanation (Business View)

Think of this service as a **Real-Time Risk Analyst**:
1.  **Portfolio Monitoring**:
    *   *Input*: It watches the output of the "Bookkeeper" (Risk Stream Processor).
    *   *Action*: "Oh, Portfolio A just bought 100 more shares of Tesla."
    
2.  **Market Awareness**:
    *   *Input*: It also (conceptually) knows the history of market movements.
    *   *Action*: "Tesla has been very volatile lately."

3.  **The "What If" Machine**:
    *   *Trigger*: Every time the portfolio changes, or on a regular heartbeat.
    *   *Process*: It runs thousands of simulations: "What if Tesla drops 2%? What if it drops 5%? What if the whole tech sector crashes?"
    *   *Output*: It produces a single number (Value at Risk): "There is a 99% chance you won't lose more than $5,000 today."
    
    This number is instantly pushed to the risk dashboard, allowing managers to freeze trading if the risk gets too high.

---

### Technical Deep Dive (Engineering View)

The **Risk Calculator Service** uses **Kafka Streams** for event-driven orchestration, but the market-price lookup is now handled by **TimescaleDB** through JDBC instead of a Kafka `GlobalKTable` or local RocksDB state store.

### 1. The Processing Topology
The `RiskCalculatorTopology` is a triggered computation engine with a single input stream.

*   **Primary Stream (`risk.portfolio.state`)**: Consumes portfolio snapshots and triggers the VaR calculation.
*   **Price History Store (`market_enriched`)**: The calculator queries TimescaleDB for the latest historical closes for every symbol in the portfolio.

### 2. Runtime Data Path
The lifecycle of a single calculation is:

1.  A portfolio snapshot lands on `risk.portfolio.state`.
2.  `StatelessVaRCalculator` receives the portfolio.
3.  `MarketDataRepository` queries TimescaleDB for the last 100 `close` prices per symbol from `market_enriched`.
4.  The calculator builds a portfolio value series from that history.
5.  It computes Historical VaR and Monte Carlo VaR.
6.  It publishes the result to `risk.model.results`.

### 3. Storage Used by the Calculator
*   **TimescaleDB**: Stores market history in `market_enriched`.
*   **Kafka**: Carries the trigger event (`risk.portfolio.state`) and the output result (`risk.model.results`).
*   **HikariCP connection pool**: Manages the JDBC connections used by `MarketDataRepository`.

### End-to-End Analysis Path
This section details the lifecycle of a single risk calculation, from the moment data lands in Kafka to the final Value at Risk metric.

### Step 1: Ingestion & Trigger (The Event)
The calculation is event-driven, triggered whenever a portfolio changes.

*   **Input Component**:
    *   **Topic**: `risk.portfolio.state`.
    *   **Payload**: `RiskPortfolioState` (for example, a portfolio holding 50 AAPL and 100 TSLA).
*   **Action**:
    1.  The stream consumer picks up the message.
    2.  The portfolio ID is used as the key so updates for the same portfolio stay ordered.

### Step 2: Enrichment (The Context)
The portfolio state alone is not enough to estimate risk.
*   **Component**: `MarketDataRepository`.
*   **Lookup**:
    1.  The repository opens a pooled JDBC connection.
    2.  For each symbol in the portfolio, it queries `market_enriched` in TimescaleDB.
    3.  It returns the latest historical `close` prices ordered by time.

### Step 3: State Accumulation (The Memory)
VaR requires history, not just the present.
*   **Component**: `StatelessVaRCalculator`.
*   **Logic**:
    1.  The calculator aligns the returned per-symbol price histories by index.
    2.  It constructs a portfolio value series.
    3.  It converts that series into returns.
    4.  It uses those returns for the risk models below.

### Step 4: The Math Core (The Valuation)
Once the history is loaded, the system passes the price-derived return series to the VaR models.

**A. Time Series Construction**
It calculates the Total Portfolio Value for every snapshot in history.
*   *T-3*: $100,000 (Based on old prices)
*   *T-2*: $102,000
*   *T-1*: $98,000
*   *T-0*: $99,000 (Current)

**B. Returns Calculation**
It converts absolute values into percentage returns to normalize the data.
*   *Formula*: $Return_t = \frac{Value_t - Value_{t-1}}{Value_{t-1}}$
*   *Result*: `[+2.0%, -3.9%, +1.0%, ...]`

### Step 6: Simulation Models (The Prediction)
Now that we have the distribution of returns, we run two parallel models to predict the "Worst Case" (99% confidence).

**Model 1: Historical Simulation**
*   **Concept**: "History repeats itself."
*   **Mechanism**:
    1.  Sort the returns: `[-3.9%, -0.5%, +1.0%, +2.0%]`
    2.  Pick the 1st percentile (worst 1% event).
    3.  Result: **-3.9%**.
*   **Pros/Cons**:
    *   **Pro (Realism)**: It uses actual past data. If a crash happened, it's included. It doesn't rely on theoretical assumptions.
    *   **Con (Jumpy)**: With a small window (100 ticks), "goldfish memory" is an issue. If a crash slides out of the window, risk drops instantly, even if the market is still dangerous.

**Model 2: Monte Carlo (Parametric)**
*   **Concept**: "Markets follow a Bell Curve."
*   **Mechanism**:
    1.  Calculate Mean ($\mu$) and volatility/Standard Deviation ($\sigma$).
    2.  Use the statistical Inverse CDF function to find the 99% boundary.
*   **Pros/Cons**:
    *   **Pro (Stability)**: It smooths out the data by fitting it to a mathematical curve. One bad data point won't ruin the calculation.
    *   **Con (Naive)**: It assumes crashes are statistically impossible (Normal Distribution). In reality, markets crash often ("Fat Tails"), so this model tends to be too optimistic.

### Technical Trade-Offs

#### 1. The Cost of "Window Size"
The `MAX_WINDOW_SIZE` (currently 100) is the most critical configuration.
*   **100 Ticks**:
    *   *Pros*: Fast, low RAM, low network usage.
    *   *Cons*: Statistical noise. A single bad tick represents 1% probability, making the VaR jumpy.
*   **1000+ Ticks**:
    *   *Pros*: Smooth, statistically valid results.
    *   *Cons*: More database I/O and a heavier query path for each calculation.

#### 2. The Impact of Volatility
The system is highly sensitive to market volatility ($\sigma$).
*   If a stock like TSLA starts moving erratically, the standard deviation of the price-return series increases immediately.
*   This causes the "Bell Curve" to widen, pushing the 99% cutoff point further down, automatically increasing the reported Value at Risk.

### Key Differences from Stream Processor
| Feature | Risk Stream Processor | Risk Calculator Service |
| :--- | :--- | :--- |
| **Primary Goal** | Data Normalization & Reduction | Complex Mathematical Computation |
| **State Type** | Aggregates (Sums, OHLC) | Database-backed price history lookup |
| **CPU Profile** | Low (I/O Bound) | High (CPU Bound) |
| **Scale Strategy** | Scale by Partition count | Scale by CPU availability and database throughput |
