package com.var.risk.calculator.domain;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalWindow {
    private List<EnrichedPortfolioState> history = new ArrayList<>();
}
