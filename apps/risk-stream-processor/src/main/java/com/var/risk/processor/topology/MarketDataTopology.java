package com.var.risk.processor.topology;

import java.time.Duration;
import java.time.Instant;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.WindowedSerdes;

import com.var.risk.processor.domain.EnrichedTick;
import com.var.risk.processor.domain.MarketTick;
import com.var.risk.processor.serdes.JsonSerde;

public class MarketDataTopology {
    // Logger removed as it was unused
    
    public static final String INPUT_TOPIC = "market.raw.prices";
    public static final String OUTPUT_TOPIC = "market.enriched";

    public static void build(StreamsBuilder builder) {
        KStream<String, MarketTick> marketStream = builder.stream(
            INPUT_TOPIC,
            Consumed.with(Serdes.String(), JsonSerde.serde(MarketTick.class))
        );

        marketStream
            // 1. Ensure Key is Symbol
            .selectKey((key, tick) -> tick.getSymbol())
            
            // 2. Group by Symbol
            .groupByKey(Grouped.with(Serdes.String(), JsonSerde.serde(MarketTick.class)))
            
            // 3. Window: 1 Second Tumbling Window
            .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofSeconds(1), Duration.ofMillis(500)))
            
            // 4. Aggregate OHLC
            .aggregate(
                // Initializer
                () -> new EnrichedTick(),
                // Aggregator
                (key, tick, agg) -> {
                    if (agg.getSymbol() == null) {
                        // First tick in window
                        agg.setSymbol(key);
                        agg.setOpen(tick.getLast());
                        agg.setHigh(tick.getLast());
                        agg.setLow(tick.getLast());
                        agg.setClose(tick.getLast());
                        agg.setVolume(tick.getVolume()); // Or 0 if volume is cumulative, but assuming tick volume here
                    } else {
                        // Subsequent ticks
                        agg.setHigh(Math.max(agg.getHigh(), tick.getLast()));
                        agg.setLow(Math.min(agg.getLow(), tick.getLast()));
                        agg.setClose(tick.getLast());
                        agg.setVolume(agg.getVolume() + tick.getVolume());
                    }
                    return agg;
                },
                Materialized.with(Serdes.String(), JsonSerde.serde(EnrichedTick.class))
            )
            
            // 5. Convert to Stream & attach Window Times
            .toStream()
            .mapValues((windowKey, aggTick) -> {
                aggTick.setStartTime(Instant.ofEpochMilli(windowKey.window().start()));
                aggTick.setEndTime(Instant.ofEpochMilli(windowKey.window().end()));
                return aggTick;
            })
            
            // 6. Sink to Output Topic
            .to(OUTPUT_TOPIC, Produced.with(WindowedSerdes.timeWindowedSerdeFrom(String.class, 1000L), JsonSerde.serde(EnrichedTick.class)));
            
            // Note: The key in the output topic will be Windowed<String>. 
            // Often downstream consumers prefer just String key. 
            // If we want simple string key output, we should use selectKey before .to()
            // Let's stick with Windowed Key for now or remap if requested.
            // Actually, for easy consumption, let's discard the window from the key in the sink
            // so downstream sees Key="AAPL", Value={... window info inside ...}
            
        // Refined Sink:
        // .selectKey((windowKey, value) -> windowKey.key())
        // .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), JsonSerde.serde(EnrichedTick.class)));
    }
    
    public static void buildSimple(StreamsBuilder builder) {
         // Alternative implementation with simple key output
         builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), JsonSerde.serde(MarketTick.class)))
            .selectKey((key, tick) -> tick.getSymbol())
            .groupByKey(Grouped.with(Serdes.String(), JsonSerde.serde(MarketTick.class)))
            .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofSeconds(1), Duration.ofMillis(500)))
            .aggregate(
                EnrichedTick::new,
                (key, tick, agg) -> updateOHLC(key, tick, agg),
                Materialized.with(Serdes.String(), JsonSerde.serde(EnrichedTick.class))
            )
            .toStream()
            .map((windowKey, aggTick) -> {
                aggTick.setStartTime(Instant.ofEpochMilli(windowKey.window().start()));
                aggTick.setEndTime(Instant.ofEpochMilli(windowKey.window().end()));
                return KeyValue.pair(windowKey.key(), aggTick);
            })
            .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), JsonSerde.serde(EnrichedTick.class)));
    }

    private static EnrichedTick updateOHLC(String key, MarketTick tick, EnrichedTick agg) {
        if (agg.getSymbol() == null) {
            agg.setSymbol(key);
            agg.setOpen(tick.getLast());
            agg.setHigh(tick.getLast());
            agg.setLow(tick.getLast());
            agg.setClose(tick.getLast());
            agg.setVolume(tick.getVolume()); 
        } else {
            agg.setHigh(Math.max(agg.getHigh(), tick.getLast()));
            agg.setLow(Math.min(agg.getLow(), tick.getLast()));
            agg.setClose(tick.getLast());
            agg.setVolume(agg.getVolume() + tick.getVolume());
        }
        return agg;
    }
}
