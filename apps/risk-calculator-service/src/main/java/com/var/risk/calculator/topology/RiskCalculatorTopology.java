package com.var.risk.calculator.topology;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

import com.var.risk.calculator.domain.EnrichedPortfolioState;
import com.var.risk.calculator.domain.EnrichedTick;
import com.var.risk.calculator.domain.HistoricalWindow;
import com.var.risk.calculator.domain.RiskPortfolioState;
import com.var.risk.calculator.domain.RiskResult;
import com.var.risk.calculator.serdes.JsonSerde;
import com.var.risk.calculator.service.VaRCalculator;

public class RiskCalculatorTopology {

    public static final String INPUT_TOPIC = "risk.portfolio.state";
    public static final String MARKET_TOPIC = "market.enriched";
    public static final String OUTPUT_TOPIC = "risk.model.results";
    public static final String MARKET_STORE = "market-store";
    private static final int MAX_WINDOW_SIZE = 100;

    public static void build(StreamsBuilder builder) {
        VaRCalculator calculator = new VaRCalculator();

        // -------------------------------------------------------------------------
        // Step 1: Storage (The Foundation)
        // GlobalKTable for Market Data backed by RocksDB ("market-store").
        // Ensures every pod has local access to the entire stock market state.
        // See README.md "Step 1: Storage"
        // -------------------------------------------------------------------------
        GlobalKTable<String, EnrichedTick> marketTable = builder.globalTable(
            MARKET_TOPIC,
            Consumed.with(Serdes.String(), JsonSerde.serde(EnrichedTick.class)),
            Materialized.as(MARKET_STORE)
        );

        // -------------------------------------------------------------------------
        // Step 2: Ingestion & Trigger (The Event)
        // Consumes portfolio snapshots. This triggers the calculation logic.
        // -------------------------------------------------------------------------
        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), JsonSerde.serde(RiskPortfolioState.class)))
                
                // ---------------------------------------------------------------------
                // Step 3: Enrichment (The Context)
                // Performs the "Stream-Table Join" to attach real-time market prices
                // to the portfolio assets using the local RocksDB store.
                // ---------------------------------------------------------------------
                .transform(() -> new PortfolioPricer(MARKET_STORE))
                .groupByKey(Grouped.with(Serdes.String(), JsonSerde.serde(EnrichedPortfolioState.class)))
                
                // ---------------------------------------------------------------------
                // Step 4: State Accumulation (The Memory)
                // Maintains the Sliding Window of history (Last 100 ticks).
                // Uses RocksDB to persist the list.
                // ---------------------------------------------------------------------
                .aggregate(
                        HistoricalWindow::new,
                        (key, value, aggregate) -> {
                            List<EnrichedPortfolioState> list = new ArrayList<>(aggregate.getHistory());
                            list.add(value);
                            // Sliding Window Logic: Drop oldest if > MAX_WINDOW_SIZE
                            if (list.size() > MAX_WINDOW_SIZE) {
                                list.remove(0);
                            }
                            aggregate.setHistory(list);
                            return aggregate;
                        },
                        Materialized.with(Serdes.String(), JsonSerde.serde(HistoricalWindow.class))
                )
                .toStream()
                
                // ---------------------------------------------------------------------
                // Step 5 & 6: Math Core & Simulation Models
                // Calculates Value, Returns, and runs Historical + Monte Carlo VaR models.
                // ---------------------------------------------------------------------
                .mapValues((key, window) -> calculator.calculate(key, window.getHistory()))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), JsonSerde.serde(RiskResult.class)));
    }

    public static class PortfolioPricer implements Transformer<String, RiskPortfolioState, KeyValue<String, EnrichedPortfolioState>> {
        private final String storeName;
        private KeyValueStore<String, ValueAndTimestamp<EnrichedTick>> marketStore;
        private ProcessorContext context;

        public PortfolioPricer(String storeName) {
            this.storeName = storeName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void init(ProcessorContext context) {
            this.context = context;
            // Get access to the local RocksDB store configured in the topology builder
            this.marketStore = (KeyValueStore<String, ValueAndTimestamp<EnrichedTick>>) context.getStateStore(storeName);
        }

        /**
         * Step 3 Logic: The Stream-Table Join
         * Iterates over every position in the portfolio and finds the current market price
         * from the local state store.
         */
        @Override
        public KeyValue<String, EnrichedPortfolioState> transform(String key, RiskPortfolioState value) {
            List<EnrichedPortfolioState.EnrichedPosition> enrichedPositions = value.getPositions().values().stream()
                .map(pos -> {
                    // FAST LOOKUP: Reads specifically from the local RocksDB instance on this pod.
                    // No network call. < 1ms latency.
                    ValueAndTimestamp<EnrichedTick> tickWithTs = marketStore.get(pos.getInstrument());
                    EnrichedTick tick = (tickWithTs != null) ? tickWithTs.value() : null;
                    BigDecimal price = (tick != null) ? BigDecimal.valueOf(tick.getClose()) : BigDecimal.ZERO;
                    
                    return new EnrichedPortfolioState.EnrichedPosition(
                        pos.getInstrument(),
                        BigDecimal.valueOf(pos.getNetQuantity()),
                        price
                    );
                })
                .collect(Collectors.toList());

            EnrichedPortfolioState enrichedState = new EnrichedPortfolioState(
                value.getPortfolioId(),
                enrichedPositions,
                System.currentTimeMillis()
            );

            return KeyValue.pair(key, enrichedState);
        }

        @Override
        public void close() {
        }
    }
}
