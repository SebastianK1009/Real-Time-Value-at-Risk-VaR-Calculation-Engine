package com.var.risk.calculator.domain;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioState {
    private String portfolioId;
    private List<Position> positions;
    private long timestamp;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Position {
        private String symbol;
        private BigDecimal quantity;
        private BigDecimal currentPrice;
    }
}
