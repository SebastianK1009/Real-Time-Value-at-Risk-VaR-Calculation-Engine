package com.var.risk.processor;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting Risk Stream Processor...");
        
        Properties props = new Properties();
        // TODO: Load properties from config file or env vars
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "risk-stream-processor");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv().getOrDefault("KAFKA_BROKERS", "localhost:9092"));
        
        // Placeholder for Topology
        Topology topology = buildTopology();
        
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
        // TODO: Implement Topology A and B
        return new Topology();
    }
}
