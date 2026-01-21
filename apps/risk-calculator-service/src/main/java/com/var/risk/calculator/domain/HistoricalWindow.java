package com.var.risk.calculator.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalWindow {
    private List<PortfolioState> history = new ArrayList<>();
}
