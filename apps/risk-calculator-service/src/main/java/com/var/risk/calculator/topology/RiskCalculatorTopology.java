package com.var.risk.calculator.topology;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;

import com.var.risk.calculator.domain.RiskPortfolioState;
import com.var.risk.calculator.domain.RiskResult;
import com.var.risk.calculator.serdes.JsonSerde;
import com.var.risk.calculator.service.StatelessVaRCalculator;

/**
 * stateless Risk Calculator Topology
 * 
 * New Architecture:
 * 1. Consumes RiskPortfolioState (snapshot of positions).
 * 2. Fetches historical market data from TimescaleDB (vs. maintaining local state).
 * 3. Calculates VaR using current positions + historical prices.
 * 4. Produces RiskResult.
 */
public class RiskCalculatorTopology {

    public static final String INPUT_TOPIC = "risk.portfolio.state";
    public static final String OUTPUT_TOPIC = "risk.model.results";

    public static void build(StreamsBuilder builder) {
        // Instantiate the stateless calculator (which holds the DB connection pool)
        StatelessVaRCalculator calculator = new StatelessVaRCalculator();

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), JsonSerde.serde(RiskPortfolioState.class)))
                .mapValues((key, portfolio) -> calculator.calculate(key, portfolio))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), JsonSerde.serde(RiskResult.class)));
    }
}
