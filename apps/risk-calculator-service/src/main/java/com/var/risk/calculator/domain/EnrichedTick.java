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
public class EnrichedTick {
    private String symbol;
    private Instant startTime;
    private Instant endTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
