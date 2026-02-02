package com.var.risk.calculator.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.var.risk.calculator.domain.EnrichedPortfolioState;
import com.var.risk.calculator.domain.RiskPortfolioState;
import com.var.risk.calculator.domain.RiskResult;
import com.var.risk.calculator.repository.MarketDataRepository;

public class StatelessVaRCalculator {
    private static final Logger log = LoggerFactory.getLogger(StatelessVaRCalculator.class);
    private static final double CONFIDENCE_LEVEL_95 = 0.95;
    private static final double CONFIDENCE_LEVEL_99 = 0.99;
    
    private final MarketDataRepository repository;

    public StatelessVaRCalculator() {
        this.repository = new MarketDataRepository();
    }

    public RiskResult calculate(String portfolioId, RiskPortfolioState portfolio) {
        // 1. Extract symbols
        Map<String, RiskPortfolioState.Position> positions = portfolio.getPositions();
        if (positions == null || positions.isEmpty()) {
            return new RiskResult(portfolioId, BigDecimal.ZERO, BigDecimal.ZERO, System.currentTimeMillis());
        }

        // 2. Fetch History (last 100 ticks) from remote DB
        Map<String, List<BigDecimal>> marketHistory = repository.getHistoricalPrices(positions.keySet(), 100);

        // 3. Construct "Virtual History" of Portfolio Value
        // For each time step t (0 to 99), calc total portfolio value.
        // Needs alignment. Since we simple SELECT ... LIMIT 100, we assume the ticks are roughly aligned 
        // or we treat them as "recent return scenarios" regardless of exact timestamp alignment (Simulation approach).
        // For a simpler MVP, we will assume the i-th newest tick of AAPL corresponds to the i-th newest tick of MSFT.
        
        List<Double> portfolioValues = new ArrayList<>();
        int minHistorySize = Integer.MAX_VALUE;
        for (List<BigDecimal> prices : marketHistory.values()) {
            minHistorySize = Math.min(minHistorySize, prices.size());
        }

        if (minHistorySize < 2) {
            log.warn("Insufficient market history for portfolio {}. Min history: {}", portfolioId, minHistorySize);
            return new RiskResult(portfolioId, BigDecimal.ZERO, BigDecimal.ZERO, System.currentTimeMillis());
        }

        for (int i = 0; i < minHistorySize; i++) {
            double totalValueAtT = 0.0;
            for (Map.Entry<String, RiskPortfolioState.Position> entry : positions.entrySet()) {
                String symbol = entry.getKey();
                double qty = entry.getValue().getNetQuantity();
                List<BigDecimal> prices = marketHistory.get(symbol);
                if (prices != null && i < prices.size()) {
                    totalValueAtT += qty * prices.get(i).doubleValue();
                }
            }
            portfolioValues.add(totalValueAtT);
        }

        // 3b. Reverse list so it is Oldest -> Newest (DB returns DESC/Newest first)
        Collections.reverse(portfolioValues);

        // 4. Calculate Returns
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

        // 5. Run Models
        double historicalVaR = calculateHistoricalVaR(returns, currentPortfolioValue, CONFIDENCE_LEVEL_99);
        double monteCarloVaR = calculateMonteCarloVaR(returns, currentPortfolioValue, CONFIDENCE_LEVEL_99);

        log.info("Calculated VaR for {}: Value=${}, H-VaR={}, MC-VaR={}", portfolioId, currentPortfolioValue, historicalVaR, monteCarloVaR);

        return new RiskResult(
            portfolioId,
            BigDecimal.valueOf(historicalVaR),
            BigDecimal.valueOf(monteCarloVaR),
            System.currentTimeMillis()
        );
    }

    private double calculateHistoricalVaR(List<Double> returns, double currentValue, double confidenceLevel) {
        List<Double> sortedReturns = new ArrayList<>(returns);
        Collections.sort(sortedReturns);
        int index = (int) ((1.0 - confidenceLevel) * sortedReturns.size());
        index = Math.max(0, index);
        double varReturn = sortedReturns.get(index);
        return Math.abs(currentValue * varReturn);
    }

    private double calculateMonteCarloVaR(List<Double> returns, double currentValue, double confidenceLevel) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        returns.forEach(stats::addValue);
        double mean = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        if (stdDev == 0) return 0.0;
        NormalDistribution dist = new NormalDistribution(mean, stdDev);
        double varReturn = dist.inverseCumulativeProbability(1.0 - confidenceLevel);
        return Math.abs(currentValue * varReturn);
    }
}
