package com.var.risk.processor.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskPortfolioState {
    private String portfolioId;
    
    // Key: Instrument (Symbol)
    @Builder.Default
    private Map<String, PortfolioPosition> positions = new HashMap<>();
    
    public RiskPortfolioState updateFromTrade(TradeEvent trade) {
        if (this.portfolioId == null) {
            this.portfolioId = trade.getPortfolioId();
        }
        
        PortfolioPosition pos = positions.getOrDefault(trade.getInstrument(), 
            PortfolioPosition.builder()
                .portfolioId(trade.getPortfolioId())
                .instrument(trade.getInstrument())
                .netQuantity(0.0)
                .build()
        );
        
        double qtyChange = "BUY".equalsIgnoreCase(trade.getDirection()) ? trade.getQuantity() : -trade.getQuantity();
        
        pos.setNetQuantity(pos.getNetQuantity() + qtyChange);
        pos.setLastUpdateId(trade.getTradeId());
        pos.setTimestamp(trade.getTimestamp());
        
        positions.put(trade.getInstrument(), pos);
        return this;
    }
}
