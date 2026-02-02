package com.var.risk.calculator.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class MarketDataRepository {
    private static final Logger log = LoggerFactory.getLogger(MarketDataRepository.class);
    private final DataSource dataSource;

    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "timescaledb");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "5432");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "postgres");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "postgres");
    private static final String DB_PASS = System.getenv().getOrDefault("DB_PASS", "password");

    public MarketDataRepository() {
        HikariConfig config = new HikariConfig();
        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", DB_HOST, DB_PORT, DB_NAME);
        log.info("Connecting to Database: {}", jdbcUrl);
        
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASS);
        
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        
        this.dataSource = new HikariDataSource(config);
    }

    /**
     * Retrieves the latest 100 closing prices for the given symbols.
     * Returns a map of Symbol -> List<BigDecimal> (ordered by time DESC - newest first)
     */
    public Map<String, List<BigDecimal>> getHistoricalPrices(Set<String> symbols, int limit) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<BigDecimal>> history = new HashMap<>();
        for (String symbol : symbols) {
            history.put(symbol, new ArrayList<>());
        }

        // Construct query: SELECT symbol, close FROM market_enriched WHERE symbol IN (...) ORDER BY time DESC LIMIT ...
        // Note: For efficient per-symbol limits, we might want to loop or use window functions.
        // Given the small number of symbols in this demo (5), looping is acceptable and simpler.
        
        String sql = "SELECT close FROM market_enriched WHERE symbol = ? ORDER BY time DESC LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            for (String symbol : symbols) {
                ps.setString(1, symbol);
                ps.setInt(2, limit);
                
                try (ResultSet rs = ps.executeQuery()) {
                    List<BigDecimal> prices = history.get(symbol);
                    while (rs.next()) {
                        BigDecimal close = rs.getBigDecimal("close");
                        if (close != null) {
                            prices.add(close);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error fetching historical prices: {}", e.getMessage());
        }

        return history;
    }
}
