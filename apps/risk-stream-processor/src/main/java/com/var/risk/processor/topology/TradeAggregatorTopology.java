package com.var.risk.processor.topology;

import com.var.risk.processor.domain.RiskPortfolioState;
import com.var.risk.processor.domain.TradeEvent;
import com.var.risk.processor.serdes.JsonSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeAggregatorTopology {
    private static final Logger logger = LoggerFactory.getLogger(TradeAggregatorTopology.class);
    
    public static final String INPUT_TOPIC = "trades.raw.events";
    public static final String OUTPUT_TOPIC = "risk.portfolio.state";

    public static void build(StreamsBuilder builder) {
        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), JsonSerde.serde(TradeEvent.class)))
            // 1. Ensure Key is PortfolioId
            .selectKey((key, trade) -> trade.getPortfolioId())
            
            // 2. Group by Portfolio
            .groupByKey()
            
            // 3. Aggregate State
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
