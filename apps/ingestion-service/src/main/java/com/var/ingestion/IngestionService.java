package com.var.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Configuration - Connection and Topic Settings
    /** Hostname of the market data simulator service */
    private static final String SIMULATOR_HOST = System.getenv().getOrDefault("SIMULATOR_HOST", "market-data-simulator");
    
    /** Port number for the simulator socket connection */
    private static final int SIMULATOR_PORT = Integer.parseInt(System.getenv().getOrDefault("SIMULATOR_PORT", "9999"));
    
    /** Kafka broker bootstrap servers for initial connection */
    private static final String KAFKA_BOOTSTRAP_SERVERS = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
    
    /** Kafka topic where market data messages are published */
    private static final String KAFKA_TOPIC = System.getenv().getOrDefault("KAFKA_TOPIC", "market_data");
    
    /** Retry interval in seconds when connection or producer initialization fails */
    private static final int RETRY_INTERVAL_SECONDS = Integer.parseInt(System.getenv().getOrDefault("RETRY_INTERVAL", "5"));

    public static void main(String[] args) {
        logger.info("Starting Java Ingestion Service...");
        logger.info("Configuration: Simulator={}:{}, Kafka={}", SIMULATOR_HOST, SIMULATOR_PORT, KAFKA_BOOTSTRAP_SERVERS);

        KafkaProducer<String, String> producer = null;
        Socket socket = null;
        BufferedReader reader = null;

        while (true) {
            try {
                // Initialize Kafka Producer if needed
                if (producer == null) {
                    producer = createKafkaProducer();
                    if (producer == null) {
                        logger.warn("Kafka Producer could not be created. Retrying in {} seconds...", RETRY_INTERVAL_SECONDS);
                        Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_INTERVAL_SECONDS));
                        continue;
                    }
                }

                // Connect to Simulator
                if (socket == null || socket.isClosed()) {
                    socket = connectToSimulator();
                    if (socket == null) {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_INTERVAL_SECONDS));
                        continue;
                    }
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                }

                // Read Loop
                String line;
                while ((line = reader.readLine()) != null) {
                    if (producer != null) {
                        processMessage(producer, line);
                    } else {
                        logger.error("Producer became null unexpectedly.");
                        break;
                    }
                }

                // If readLine returns null, connection closed
                logger.warn("Simulator closed connection.");
                closeQuietly(socket);
                socket = null;

            } catch (Exception e) {
                logger.error("Error in main loop: {}", e.getMessage(), e);
                closeQuietly(socket);
                socket = null;
                // Don't close producer usually, unless fatal error
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_INTERVAL_SECONDS));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private static KafkaProducer<String, String> createKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Optimize for throughput
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5); 
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        
        try {
            KafkaProducer<String, String> p = new KafkaProducer<>(props);
            logger.info("Connected to Kafka at {}", KAFKA_BOOTSTRAP_SERVERS);
            return p;
        } catch (Exception e) {
            logger.error("Failed to connect to Kafka: {}", e.getMessage());
            return null;
        }
    }

    private static Socket connectToSimulator() {
        try {
            Socket s = new Socket(SIMULATOR_HOST, SIMULATOR_PORT);
            s.setSoTimeout(0); // Infinite timeout for reading
            logger.info("Connected to Simulator at {}:{}", SIMULATOR_HOST, SIMULATOR_PORT);
            return s;
        } catch (IOException e) {
            logger.error("Failed to connect to Simulator: {}", e.getMessage());
            return null;
        }
    }

    private static void processMessage(KafkaProducer<String, String> producer, String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson); // Parse JSON
            String type = root.path("type").asText(); // Get message type

            if ("market_data".equals(type)) {
                // Determine partition key (Symbol)
                // String symbol = root.path("symbol").asText();
                // We send the whole JSON as value
                
                // Using null key for round-robin or symbol as key for ordering?
                // Let's use Null Key for now unless ordering per symbol is strictly required by partition
                // producer.send(new ProducerRecord<>(KAFKA_TOPIC, symbol, rawJson));
                
                producer.send(new ProducerRecord<>(KAFKA_TOPIC, null, rawJson), (metadata, exception) -> {
                    if (exception != null) {
                        logger.error("Kafka Write Error: {}", exception.getMessage());
                    }
                });
            } else if ("welcome".equals(type)) {
                logger.info("Received Welcome: {}", root.path("message").asText());
            }

        } catch (Exception e) {
            logger.error("Failed to parse/send message: {}", e.getMessage());
        }
    }
    
    private static void closeQuietly(Socket s) {
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }
}
