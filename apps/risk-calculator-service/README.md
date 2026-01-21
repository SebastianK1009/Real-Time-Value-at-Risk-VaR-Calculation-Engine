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

## Technical Deep Dive (Engineering View)

The **Risk Calculator Service** works slightly differently from the upstream processor. While it also uses **Kafka Streams**, its focus is on **Complex Event Processing (CEP)** and **compute-heavy payloads** rather than just high-throughput aggregation.

### 1. The Processing Topology
The `RiskCalculatorTopology` is designed as a triggered computation engine with **Dual Input Streams**.

*   **Primary Stream (`risk.portfolio.state`)**: Consumes snapshots of what a portfolio currently holds. This triggers the calculation.
*   **Reference Table (`market.enriched`)**: Consumes the latest market prices for all symbols.
    *   **Architecture**: Uses a **GlobalKTable**. This replicates the entire market state to *every* instance of the service.
    *   **Reasoning**: This allows any portfolio (on any partition) to instantly look up the price of any stock (e.g., AAPL) without complex re-partitioning or network hops during the calculation.

### 2. The Enrichment Step (`PortfolioPricer`)
Before calculation, the service performs a real-time join:
1.  A Portfolio State event arrives (containing "User owns 50 AAPL").
2.  The `PortfolioPricer` intercepts the message.
3.  It queries the local `market-store` (GlobalKTable) for the absolute latest price of AAPL.
4.  It updates the message with this fresh price.
*   **Benefit**: If a portfolio hasn't traded for hours, its internal price data might be stale. This step ensures the VaR model always uses *current* market conditions.

### 3. The Calculation Core (`VaRCalculator`)
This is where the CPU cycles are spent.
*   **Trigger**: The Kafka Streams DSL `.mapValues()` operation invokes the calculator for each window update.
*   **Models**:
    *   **Historical VaR**: Looks at the specific returns of the assets in the portfolio over the stored window.
    *   **Monte Carlo**: Generates random market scenarios based on statistical properties (mean, variance) derived from the window.
*   **Output**: A `RiskResult` object containing the computed metrics, pushed to `risk.model.results`.

### 4. State Store Strategy
*   **Custom Serdes**: Uses `JsonSerde` to serialize complex objects (`HistoricalWindow`, `PortfolioState`) into RocksDB.
*   **Bounded State**: The topology explicitly limits the list size (`MAX_WINDOW_SIZE = 100`). This is crucial. Without this check, the `HistoricalWindow` object would grow indefinitely, eventually causing `OutOfMemoryError` or exceeding Kafka's default message size limits when backing up to the changelog.
*   **Global State**: Uses a `GlobalKTable` backed by a local state store (`market-store`) to keep a materialized view of the latest market prices for efficient lookups.

### Key Differences from Stream Processor
| Feature | Risk Stream Processor | Risk Calculator Service |
| :--- | :--- | :--- |
| **Primary Goal** | Data Normalization & Reduction | complex Mathematical Computation |
| **State Type** | Aggregates (Sums, OHLC) | Collections (Lists/Windows of History) |
| **CPU Profile** | low (I/O Bound) | High (CPU Bound) |
| **Scale Strategy** | Scale by Partition count | Scale by CPU availability (Vertical or Horizontal) |
