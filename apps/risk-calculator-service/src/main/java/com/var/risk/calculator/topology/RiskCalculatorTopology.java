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
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

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

        // 1. GlobalKTable for Market Data
        GlobalKTable<String, EnrichedTick> marketTable = builder.globalTable(
            MARKET_TOPIC,
            Consumed.with(Serdes.String(), JsonSerde.serde(EnrichedTick.class)),
            Materialized.as(MARKET_STORE)
        );

        // 2. Stream Portfolio State
        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), JsonSerde.serde(RiskPortfolioState.class)))
                // 3. Enrich with Market Data
                .transform(() -> new PortfolioPricer(MARKET_STORE))
                .groupByKey()
                .aggregate(
                        HistoricalWindow::new,
                        (key, value, aggregate) -> {
                            List<EnrichedPortfolioState> list = new ArrayList<>(aggregate.getHistory());
                            list.add(value);
                            if (list.size() > MAX_WINDOW_SIZE) {
                                list.remove(0);
                            }
                            aggregate.setHistory(list);
                            return aggregate;
                        },
                        Materialized.with(Serdes.String(), JsonSerde.serde(HistoricalWindow.class))
                )
                .toStream()
                .mapValues((key, window) -> calculator.calculate(key, window.getHistory()))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), JsonSerde.serde(RiskResult.class)));
    }

    public static class PortfolioPricer implements Transformer<String, RiskPortfolioState, KeyValue<String, EnrichedPortfolioState>> {
        private final String storeName;
        private KeyValueStore<String, EnrichedTick> marketStore;
        private ProcessorContext context;

        public PortfolioPricer(String storeName) {
            this.storeName = storeName;
        }

        @Override
        public void init(ProcessorContext context) {
            this.context = context;
            this.marketStore = (KeyValueStore<String, EnrichedTick>) context.getStateStore(storeName);
        }

        @Override
        public KeyValue<String, EnrichedPortfolioState> transform(String key, RiskPortfolioState value) {
            List<EnrichedPortfolioState.EnrichedPosition> enrichedPositions = value.getPositions().values().stream()
                .map(pos -> {
                    EnrichedTick tick = marketStore.get(pos.getInstrument());
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
