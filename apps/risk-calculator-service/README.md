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

### End-to-End Analysis Path
This section details the lifecycle of a single risk calculation, from the moment data lands in Kafka to the final Value at Risk metric.

### Step 1: Storage (The Foundation)
Before any calculation can happen, the system ensures all necessary market data is available locally on every computation node.

*   **Input Components**:
    *   **Topic**: `market.enriched` (The source of truth for prices).
    *   **Mechanism**: `GlobalKTable`.
    *   **Physical Store**: **RocksDB** of every pod (named `market-store`).
*   **Detailed Mechanism**:
    *   **Background Process**: The Kafka Streams library in your app has a background thread (the "Global Consumer").
    *   **Trigger**: As soon as a message lands in `market.enriched`:
        1.  The Global Consumer picks it up.
        2.  It creates a key-value pair.
        3.  It instantly writes/updates that row in the local **RocksDB**.
    *   **Result**: Every pod has a complete, millisecond-fresh copy of the entire stock market (prices) on its local SSD, preventing the need for network lookups during hot-path processing.

### Step 2: Ingestion & Trigger (The Event)
The calculation is event-driven, triggered effectively whenever a portfolio changes.

*   **Input Component**:
    *   **Topic**: `risk.portfolio.state`.
    *   **Payload**: `RiskPortfolioState` (e.g., "Portfolio 'A' now holds 50 AAPL and 100 TSLA").
*   **Action**:
    1.  The stream consumer picks up the message.
    2.  The "Key" is the Portfolio ID (ensuring all updates for Portfolio 'A' go to the same thread).

### Step 3: Enrichment (The Context)
The portfolio state alone is useless without knowing what the assets are worth.
*   **Component**: `PortfolioPricer` (Transformer).
*   **The "Stream-Table Join"**:
    1.  The `PortfolioPricer` receives the portfolio snapshot.
    2.  It iterates through every asset (AAPL, TSLA).
    3.  It queries the local RocksDB (`market-store`) for the latest price.
    4.  It constructs an `EnrichedPortfolioState` where each asset has a `referencePrice` attached.

### Step 4: State Accumulation (The Memory)
VaR requires history, not just the present. We need to know how the portfolio *would have* performed in the past.
*   **Component**: `.aggregate()` (Stateful Operation).
*   **Store**: `HistoricalWindow` (in RocksDB).
*   **Logic**:
    1.  The logic retrieves the `HistoricalWindow` list for this portfolio from RocksDB.
    2.  It appends the new `EnrichedPortfolioState` to the list.
    3.  **Sliding Window**: If the list size exceeds `MAX_WINDOW_SIZE` (100), the oldest entry is dropped (FIFO).
    4.  The updated list is saved back to RocksDB.

### Step 5: The Math Core (The Valuation)
Once the window is updated, the system passes the entire list of 100 snapshots to the `VaRCalculator`.

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
    *   *Cons*: **Write Amplification**. Every update requires serializing/deserializing a massive list and sending it over the network (Kafka Changelog), crushing performance.

#### 2. The Impact of Volatility
The system is highly sensitive to market volatility ($\sigma$).
*   If a stock like TSLA starts moving erratically, the Standard Deviation of the `HistoricalWindow` increases immediately.
*   This causes the "Bell Curve" to widen, pushing the 99% cutoff point further down, automatically increasing the reported Value at Risk.

### Key Differences from Stream Processor
| Feature | Risk Stream Processor | Risk Calculator Service |
| :--- | :--- | :--- |
| **Primary Goal** | Data Normalization & Reduction | Complex Mathematical Computation |
| **State Type** | Aggregates (Sums, OHLC) | Collections (Lists/Windows of History) |
| **CPU Profile** | Low (I/O Bound) | High (CPU Bound) |
| **Scale Strategy** | Scale by Partition count | Scale by CPU availability (Vertical or Horizontal) |
