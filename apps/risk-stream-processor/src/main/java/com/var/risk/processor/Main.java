package com.var.risk.processor;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.var.risk.processor.topology.MarketDataTopology;
import com.var.risk.processor.topology.TradeAggregatorTopology;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting Risk Stream Processor...");
        
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "risk-stream-processor");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv().getOrDefault("KAFKA_BROKERS", "localhost:9092"));
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        /* [Technical Deep Dive: State Management (RocksDB)]
         * The "Brain" acts as Short-Term Memory.
         * Stored locally on the container's disk (low latency) via RocksDB/JNI.
         * K8s Note: If not backed by PVC, this is lost on pod restart (Ephemeral State).
         */
        String stateDir = System.getenv().getOrDefault("KAFKA_STREAMS_STATE_DIR", "/tmp/kafka-streams");
        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000); // Commit often for demo purposes
        
        /* [Technical Deep Dive: The Processing Graph] 
         * Builds the Topology (DAG) of processing nodes. 
         */
        Topology topology = buildTopology();
        logger.info("Topology description: {}", topology.describe());
        
        KafkaStreams streams = new KafkaStreams(topology, props);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
    
    private static Topology buildTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        
        // 1. Market Data Topology (Enrichment + OHLC)
        MarketDataTopology.build(builder);
        
        // 2. Trade Data Topology (Aggregation by Portfolio)
        TradeAggregatorTopology.build(builder);
        
        return builder.build();
    }
}
