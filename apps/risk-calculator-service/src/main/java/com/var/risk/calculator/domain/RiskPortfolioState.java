package com.var.risk.calculator.domain;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskPortfolioState {
    private String portfolioId;
    
    @Builder.Default
    private Map<String, PortfolioPosition> positions = new HashMap<>();
}
