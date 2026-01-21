package com.var.risk.calculator.service;

import com.var.risk.calculator.domain.PortfolioState;
import com.var.risk.calculator.domain.RiskResult;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class VaRCalculator {
    private static final Logger log = LoggerFactory.getLogger(VaRCalculator.class);
    private static final double CONFIDENCE_LEVEL_95 = 0.95;
    private static final double CONFIDENCE_LEVEL_99 = 0.99;
    private static final int MONTE_CARLO_SIMULATIONS = 1000;

    public RiskResult calculate(String portfolioId, List<PortfolioState> history) {
        if (history == null || history.size() < 2) {
            log.warn("Insufficient history for portfolio {}: {}", portfolioId, history != null ? history.size() : 0);
            return new RiskResult(portfolioId, BigDecimal.ZERO, BigDecimal.ZERO, System.currentTimeMillis());
        }

        // 1. Calculate Portfolio Value for each snapshot in history
        List<Double> portfolioValues = history.stream()
            .map(this::calculateTotalValue)
            .collect(Collectors.toList());

        // 2. Calculate Returns
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < portfolioValues.size(); i++) {
            double prev = portfolioValues.get(i - 1);
            double curr = portfolioValues.get(i);
            if (prev != 0) {
                returns.add((curr - prev) / prev);
            }
        }

        if (returns.isEmpty()) {
            return new RiskResult(portfolioId, BigDecimal.ZERO, BigDecimal.ZERO, System.currentTimeMillis());
        }

        double currentPortfolioValue = portfolioValues.get(portfolioValues.size() - 1);

        // 3. Historical VaR
        double historicalVaR = calculateHistoricalVaR(returns, currentPortfolioValue, CONFIDENCE_LEVEL_99);

        // 4. Monte Carlo VaR
        double monteCarloVaR = calculateMonteCarloVaR(returns, currentPortfolioValue, CONFIDENCE_LEVEL_99);

        log.info("Calculated VaR for {}: Historical={}, MonteCarlo={}", portfolioId, historicalVaR, monteCarloVaR);

        return new RiskResult(
            portfolioId,
            BigDecimal.valueOf(historicalVaR),
            BigDecimal.valueOf(monteCarloVaR),
            System.currentTimeMillis()
        );
    }

    private double calculateTotalValue(PortfolioState state) {
        if (state.getPositions() == null) return 0.0;
        return state.getPositions().stream()
            .mapToDouble(p -> 
                p.getQuantity().doubleValue() * 
                (p.getCurrentPrice() != null ? p.getCurrentPrice().doubleValue() : 0.0)
            )
            .sum();
    }

    private double calculateHistoricalVaR(List<Double> returns, double currentValue, double confidenceLevel) {
        List<Double> sortedReturns = new ArrayList<>(returns);
        Collections.sort(sortedReturns);

        // For confidence level 0.99 (99%), we look at the bottom 1% of returns.
        int index = (int) Math.ceil((1.0 - confidenceLevel) * sortedReturns.size()) - 1;
        if (index < 0) index = 0;

        double worstReturn = sortedReturns.get(index);
        
        // VaR is expressed as a positive loss amount
        return Math.abs(worstReturn * currentValue);
    }

    private double calculateMonteCarloVaR(List<Double> returns, double currentValue, double confidenceLevel) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        returns.forEach(stats::addValue);

        double mean = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        
        // Prevent division by zero or invalid stats
        if (Double.isNaN(stdDev) || stdDev == 0) return 0.0;

        NormalDistribution dist = new NormalDistribution(mean, stdDev);
        
        // Inverse Cumulative Distribution Function to find the return at the percentile
        // e.g. for 99% confidence, we want the point where 1% of the distribution lies to the left.
        double percentileReturn = dist.inverseCumulativeProbability(1.0 - confidenceLevel);

        return Math.abs(percentileReturn * currentValue);
    }
}
