package com.var.ingestion;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public class MarketDataRepository {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataRepository.class);
    private final HikariDataSource dataSource;

    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "timescaledb");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "5432");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "postgres");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "postgres");
    private static final String DB_PASS = System.getenv().getOrDefault("DB_PASS", "password");

    public MarketDataRepository() {
        HikariConfig config = new HikariConfig();
        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", DB_HOST, DB_PORT, DB_NAME);
        logger.info("Connecting to Database: {}", jdbcUrl);
        
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASS);
        
        config.setMaximumPoolSize(5); 
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000); // 5 seconds
        
        this.dataSource = new HikariDataSource(config);
    }

    public void saveMarketTick(String symbol, double price, double open, double high, double low, double close, long volume, String timestampStr) {
        String sql = "INSERT INTO market_enriched (time, symbol, price, open, high, low, close, volume, start_time, end_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            Instant instant = Instant.parse(timestampStr);
            Timestamp ts = Timestamp.from(instant);
            
            ps.setTimestamp(1, ts);
            ps.setString(2, symbol);
            ps.setBigDecimal(3, java.math.BigDecimal.valueOf(price)); // price/last
            ps.setBigDecimal(4, java.math.BigDecimal.valueOf(open)); // using same as last for tick
            ps.setBigDecimal(5, java.math.BigDecimal.valueOf(high));
            ps.setBigDecimal(6, java.math.BigDecimal.valueOf(low));
            ps.setBigDecimal(7, java.math.BigDecimal.valueOf(close)); // using same as last for tick
            ps.setLong(8, volume);
            ps.setTimestamp(9, ts);
            ps.setTimestamp(10, ts);
            
            ps.executeUpdate();
            
        } catch (SQLException e) {
            logger.error("Error saving market tick for {}: {}", symbol, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error saving market tick: {}", e.getMessage());
        }
    }
}
