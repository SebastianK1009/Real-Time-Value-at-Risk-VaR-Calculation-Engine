package com.var.risk.calculator.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
