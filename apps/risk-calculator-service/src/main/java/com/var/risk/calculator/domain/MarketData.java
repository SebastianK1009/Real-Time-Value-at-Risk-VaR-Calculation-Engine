package com.var.risk.calculator.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketData {
    private String symbol;
    private double close;
    private long volume;
    private String timestamp; // Using String for simplicity in JSON, or could be Instant
}
