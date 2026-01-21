package com.var.risk.processor.topology;

import java.time.Duration;
import java.time.Instant;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;

import com.var.risk.processor.domain.EnrichedTick;
import com.var.risk.processor.domain.MarketTick;
import com.var.risk.processor.serdes.JsonSerde;

public class MarketDataTopology {
    // Logger removed as it was unused
    
    public static final String INPUT_TOPIC = "market.raw.prices";
    public static final String OUTPUT_TOPIC = "market.enriched";

    public static void build(StreamsBuilder builder) {
         // Step 1: Input Source
         // Read raw market data from the Kafka topic 'market.raw.prices'
         // Deserialize JSON data into MarketTick objects
         builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), JsonSerde.serde(MarketTick.class)))
            // Step 2: Rekey
            // Ensure data is partitioned by stock Symbol (e.g., "AAPL") so all ticks for a stock process together
            .selectKey((key, tick) -> tick.getSymbol())
            
            // Step 3: Grouping
            // Prepare to aggregate data by the Symbol key
            .groupByKey(Grouped.with(Serdes.String(), JsonSerde.serde(MarketTick.class)))
            
            // Step 4: Windowing
            // Group ticks into 1-second time windows to calculate stats per second
            // Allows late-arriving data within 500ms grace period
            .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofSeconds(1), Duration.ofMillis(500)))
            
            // Step 5: Aggregation (OHLC Calculation)
            // For each window, calculate Open, High, Low, Close prices and total Volume
            /* [Technical Deep Dive: RocksDB State Store]
             * This operation automatically creates a local RocksDB instance to manage state.
             * 1. Read: Checks RocksDB for existing window data for this symbol.
             * 2. Update: Runs 'updateOHLC' to merge new tick with existing data.
             * 3. Write: Serializes updated EnrichedTick to JSON and saves back to RocksDB.
             * 4. Backup: Changes are also sent to a Kafka changelog topic for fault tolerance.
             */
            .aggregate(
                EnrichedTick::new,
                (key, tick, agg) -> updateOHLC(key, tick, agg),
                Materialized.with(Serdes.String(), JsonSerde.serde(EnrichedTick.class))
            )
            
            // Step 6: Formatting
            // Convert the windowed results back to a standard stream and add start/end times
            .toStream()
            .map((windowKey, aggTick) -> {
                aggTick.setStartTime(Instant.ofEpochMilli(windowKey.window().start()));
                aggTick.setEndTime(Instant.ofEpochMilli(windowKey.window().end()));
                return KeyValue.pair(windowKey.key(), aggTick);
            })
            
            // Step 7: Output Sink
            // Write the processed, enriched data to the 'market.enriched' topic
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
