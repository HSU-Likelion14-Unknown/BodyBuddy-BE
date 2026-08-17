package com.centerton.bodybuddy.domain.analysis.client;

import java.math.BigDecimal;

public record FoodNutritionEstimationInput(
        String foodName,
        BigDecimal consumedAmount,
        String consumedUnit
) {
}
