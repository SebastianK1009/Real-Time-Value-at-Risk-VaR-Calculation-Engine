package com.var.risk.calculator.domain;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedPortfolioState {
    private String portfolioId;
    private List<EnrichedPosition> positions;
    private long timestamp;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrichedPosition {
        private String symbol;
        private BigDecimal quantity;
        private BigDecimal currentPrice;
    }
}
