# Risk Stream Processor

## Overview
The **Risk Stream Processor** is the central intelligence engine of the Real-Time VaR system. It sits between the raw data ingestion layers and the final risk calculation dashboards. Its primary purpose is to transform high-velocity, chaotic streams of raw data into organized, meaningful state snapshots that are ready for financial analysis.

### The Problem it Solves
In financial markets, data arrives in a "firehose":
*   **Market Data**: Thousands of price updates (ticks) per second per stock. Most are micro-fluctuations (noise).
*   **Trade Data**: Individual buy/sell orders scattered across time.
*   **Risk Engine Needs**: A stable view of the world (e.g., "What is the price of AAPL right now?" and "How many shares of AAPL do we own right now?").

If the Risk Engine tried to process every single raw event, it would be overwhelmed. The Risk Stream Processor solves this by acting as a **Smart Buffer and Aggregator**.

---

## Non-Technical Explanation (Business View)

Imagine a busy stock exchange floor:
1.  **Market Data Topology (The Noise Filter)**:
    *   *Raw Input*: Traders shouting prices every millisecond ("AAPL is 150.01! No, 150.02! Back to 150.01!").
    *   *Process*: The processor listens for one full second, filtering out the panic.
    *   *Output*: Every second, it announces a clean summary: "In the last second, AAPL opened at 150.01, hit a high of 150.05, and closed at 150.03."
    *   *Benefit*: Downstream systems receive 1 update per second instead of 1,000, reducing load by 99.9% while preserving key data (High/Low).

2.  **Trade Aggregator Topology (The Bookkeeper)**:
    *   *Raw Input*: A stream of individual transaction slips ("Buy 10 AAPL", "Sell 5 MSFT", "Buy 20 AAPL").
    *   *Process*: The processor organizes these slips into folders (Portfolios). It keeps a running total written in pencil (State).
    *   *Output*: Whenever a trade happens, it publishes the *new total*: "Portfolio A now holds 30 AAPL and -5 MSFT."
    *   *Benefit*: The Risk Engine doesn't need to replay history to know what we own; it just reads the latest total.

---

## Technical Deep Dive (Engineering View)

The **Risk Stream Processor** is a stateful stream processing application powered by the **Kafka Streams** library. Unlike a simple consumer that reads and forgets messages, this application manages a "brain" (local state) that remembers history.

Here is the deep technical breakdown of what is happening inside the container:

### 1. The Processing Graph (Input -> Process -> Output)
The application builds a **Topology**, which is a Directed Acyclic Graph (DAG) of processing nodes.

*   **Source Nodes**: It opens persistent TCP connections to the Kafka brokers to consume from `market.raw.prices` and `trades.raw.events`.
*   **Processor Nodes**:
    *   **Deserialization**: It uses Jackson (`JsonSerde.java`) to convert raw bytes into Java POJOs (`MarketTick`, `TradeEvent`).
    *   **Rekeying (`selectKey`)**: Forces data to be organized by specific criteria (like Portfolio ID) before processing.
        *   **The Problem**: Imagine you have 3 mailboxes (Partitions) and 3 clerks (Consumers) working in parallel. If you mail a "Deposit $100" letter and a "Withdraw $50" letter, and they end up in *different* mailboxes, Clerk B might open "Withdraw" (processing it) before Clerk A has even opened "Deposit". Your account would wrongly show an overdraft error.
        *   **The Solution**: We force a rule: "All mail for Account 123 MUST go to Mailbox A". Now, Clerk A picks up "Deposit", finishes it, and *only then* picks up "Withdraw". This guarantees the math is always correct.
    *   **Stateful Transformations**: This is the core engine. It doesn't just `map()` data; it `aggregate()`s it. This requires memory of the past.

### 2. State Management (The "Brain")
The application uses **RocksDB**, an embedded high-performance key-value database running *inside* the Java process (via JNI). It acts as the "Short-Term Memory" for the processor.

#### Detailed Storage Breakdown
RocksDB stores data differently depending on the topology type (Windowed vs. Key-Value).

| Feature | Market Data Topology (`MarketDataTopology`) | Trade Aggregator Topology (`TradeAggregatorTopology`) |
| :--- | :--- | :--- |
| **Storage Pattern** | **Window Store (Buckets)** | **KeyValue Store (Ledger)** |
| **What is Stored?** | The "In-Progress Candle". <br>Key: `Symbol + TimeWindow` (e.g., `AAPL@[10:00:01]`) | The "Portfolio Ledger". <br>Key: `PortfolioID` (e.g., `User_123`) |
| **Update Frequency** | **Extreme** (Every Tick). <br>If AAPL ticks 50 times/sec, RocksDB performs 50 Read-Update-Writes on that bucket. | **Medium** (Every Trade). <br>Updates only when a specific user executes a trade. |
| **Data Lifespan** | **Ephemeral (1.5 Seconds)**. <br>Defined by `TimeWindows.ofSizeAndGrace(1s, 0.5s)`. Once the window closes + grace period passes, the bucket is logically discarded. | **Permanent**. <br>Balances must persist indefinitely. Data is never discarded unless the portfolio is explicitly deleted. |

#### Technical Implementation
*   **Local Persistence**: When processing, the app reads/writes to RocksDB on the container's local disk (microseconds latency) instead of a remote database (milliseconds latency).
*   **Changelog Topics**: To survive a crash (since Kubernetes pods are ephemeral), every write to RocksDB is also "backed up" to a hidden internal Kafka topic (e.g., `risk-stream-processor-KSTREAM-AGGREGATE-STATE-STORE...`).
*   **Restoration**: When the log shows `State transition from REBALANCING to RUNNING`, the app is reading that changelog topic to rebuild its local RocksDB database from scratch (or catching up) before it started processing new live data.

### 3. Windowing Mechanics (Time Handling)
In `MarketDataTopology`, specific time logic applies:

*   **Tumbling Windows**: It slices time into distinct 1-second buckets.
*   **Ingestion Time vs Event Time**: It uses the timestamp embedded in the message (Event Time), not the time the server read the message.
        *   **Why Event Time?** Accuracy. If a market tick generated at 10:00:01 arrives at 10:00:05 due to network lag, processing it by "arrival time" would put it in the wrong candle. Processing by "event time" ensures it is correctly placed in the 10:00:01 bucket, ensuring the OHLC chart is mathematically correct regardless of infrastructure lag.
*   **State Store**: It maintains a specialized Window Store in RocksDB that keeps multiple buckets active simultaneously until the "grace period" expires, after which it discards old buckets.

### 4. Kubernetes Implications
Based on the `k8s/deployment.yaml`:

*   **Ephemeral State**: You do not have a `PersistentVolumeClaim` mounted for `/tmp/kafka-streams`.
*   **Consequence**: If the pod restarts (e.g., `kubectl delete pod`), the local RocksDB file is lost.
*   **Recovery**: The new pod must download the entire history from the Kafka changelog topics to rebuild the state. This increases startup time ("cold start") but keeps the architecture stateless and easier to manage.

### Summary of Data Flow
1.  **Byte Stream** enters via TCP (Kafka Protocol).
2.  **Deserializer** converts to Java Objects.
3.  **Grouper** hashes the key (`symbol` or `portfolioId`) to ensure correct partitioning.
4.  **Processors** read/write to **local embedded RocksDB**.
5.  **Serializer** converts result back to Bytes.
6.  **Producer** flushes result to output topics (`market.enriched`, etc.).

### Data Flow Diagram
```mermaid
graph LR
    RawMarket[("Kafka: market.raw.prices")] -->|Stream| MD_Topo(Market Data Topology)
    RawTrades[("Kafka: trades.raw.events")] -->|Stream| TA_Topo(Trade Aggregator Topology)
    
    subgraph "Risk Stream Processor (Pod)"
        MD_Topo -->|Aggregation (1s Window)| RocksDB[(RocksDB State)]
        TA_Topo -->|Aggregation (Portfolio)| RocksDB
    end
    
    MD_Topo -->|Enriched OHLC| Enriched[("Kafka: market.enriched")]
    TA_Topo -->|Portfolio State| RiskState[("Kafka: risk.portfolio.state")]
```
