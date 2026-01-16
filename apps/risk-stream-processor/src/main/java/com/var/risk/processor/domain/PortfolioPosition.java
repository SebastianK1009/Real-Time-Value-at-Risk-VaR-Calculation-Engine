package com.var.risk.processor.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioPosition {
    private String portfolioId;
    private String instrument;
    private double netQuantity;
    private String lastUpdateId;
    private Instant timestamp;
    
    public PortfolioPosition addQuantity(double qty) {
        this.netQuantity += qty;
        return this;
    }
}
