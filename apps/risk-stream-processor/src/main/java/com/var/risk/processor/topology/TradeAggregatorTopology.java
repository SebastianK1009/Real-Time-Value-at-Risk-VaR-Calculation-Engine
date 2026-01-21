package com.var.risk.processor.topology;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;

import com.var.risk.processor.domain.RiskPortfolioState;
import com.var.risk.processor.domain.TradeEvent;
import com.var.risk.processor.serdes.JsonSerde;

public class TradeAggregatorTopology {
    
    public static final String INPUT_TOPIC = "trades.raw.events";
    public static final String OUTPUT_TOPIC = "risk.portfolio.state";

    public static void build(StreamsBuilder builder) {
        /* [Technical Deep Dive: Processing Graph]
         * Source Node for Trade Data.
         */
        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), JsonSerde.serde(TradeEvent.class)))
            /* [Technical Deep Dive: Rekeying / The "Mailbox Problem"]
             * Routes all trades for "Portfolio A" to the same consumer thread.
             * Ensures correct sequential processing of Buy/Sell orders.
             */
            .selectKey((key, trade) -> trade.getPortfolioId())
            
            // 2. Group by Portfolio
            .groupByKey()
            
            /* [Technical Deep Dive: State Management (KeyValue Store)]
             * Uses RocksDB "KeyValue Store (Ledger)".
             * Data Lifespan: Permanent. Persists indefinitely unless explicitly deleted.
             * Creates "RiskPortfolioState" which is the running total written in "pencil".
             */
            .aggregate(
                // Initializer
                () -> new RiskPortfolioState(),
                // Aggregator
                (key, trade, state) -> state.updateFromTrade(trade),
                Materialized.with(Serdes.String(), JsonSerde.serde(RiskPortfolioState.class))
            )
            // 4. Stream changes out
            .toStream()
            .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), JsonSerde.serde(RiskPortfolioState.class)));
    }
}
