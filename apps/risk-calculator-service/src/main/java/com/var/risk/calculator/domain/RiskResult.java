package com.var.risk.calculator.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskResult {
    private String portfolioId;
    private BigDecimal historicalVaR;
    private BigDecimal monteCarloVaR;
    private long calculationTimestamp;
}
