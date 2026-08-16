package com.centerton.bodybuddy.domain.recommendation.model;

import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationRes;

public record RecommendationCreationResult(
        RecommendationRes response,
        boolean createdNow
) {
}
