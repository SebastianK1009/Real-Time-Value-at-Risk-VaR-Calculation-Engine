package com.var.risk.calculator.topology;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;

import com.var.risk.calculator.domain.HistoricalWindow;
import com.var.risk.calculator.domain.PortfolioState;
import com.var.risk.calculator.domain.RiskResult;
import com.var.risk.calculator.serdes.JsonSerde;
import com.var.risk.calculator.service.VaRCalculator;

public class RiskCalculatorTopology {

    public static final String INPUT_TOPIC = "risk.portfolio.state";
    public static final String OUTPUT_TOPIC = "risk.model.results";
    private static final int MAX_WINDOW_SIZE = 100; // Example size

    public static void build(StreamsBuilder builder) {
        VaRCalculator calculator = new VaRCalculator();

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), JsonSerde.serde(PortfolioState.class)))
                .groupByKey()
                .aggregate(
                        HistoricalWindow::new,
                        (key, value, aggregate) -> {
                            List<PortfolioState> list = new ArrayList<>(aggregate.getHistory());
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
}
