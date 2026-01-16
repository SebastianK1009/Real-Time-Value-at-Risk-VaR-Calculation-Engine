package com.var.risk.processor.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeEvent {
    @JsonProperty("trade_id")
    private String tradeId;

    @JsonProperty("portfolio_id")
    private String portfolioId;

    @JsonProperty("instrument")
    private String instrument;

    @JsonProperty("quantity")
    private double quantity;

    @JsonProperty("direction")
    private String direction;

    @JsonProperty("timestamp")
    private Instant timestamp;
}
