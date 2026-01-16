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
public class MarketTick {
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("bid")
    private double bid;

    @JsonProperty("ask")
    private double ask;

    @JsonProperty("last")
    private double last;

    @JsonProperty("volume")
    private long volume;
    
    // We can include other fields if needed, but these are the core ones
    @JsonProperty("high")
    private double high;
    
    @JsonProperty("low")
    private double low;
}
