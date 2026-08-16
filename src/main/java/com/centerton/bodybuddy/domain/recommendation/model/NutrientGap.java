package com.centerton.bodybuddy.domain.recommendation.model;

import java.math.BigDecimal;

public record NutrientGap(
        BigDecimal referenceAmount,
        BigDecimal consumedAmount,
        BigDecimal gapAmount,
        BigDecimal gapRatio
) {
}
