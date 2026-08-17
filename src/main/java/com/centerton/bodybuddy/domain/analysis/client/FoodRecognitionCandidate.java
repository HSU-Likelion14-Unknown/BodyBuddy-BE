package com.centerton.bodybuddy.domain.analysis.client;

import java.math.BigDecimal;

public record FoodRecognitionCandidate(
        String foodName,
        BigDecimal confidence
) {
}
