package com.centerton.bodybuddy.domain.recommendation.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        name = "bodybuddy.recommendation.ai-fallback-provider",
        havingValue = "fake",
        matchIfMissing = true
)
public class NoOpAiIngredientRecommendationClient implements AiIngredientRecommendationClient {
    @Override
    public List<AiIngredientCandidate> recommend(AiIngredientRecommendationInput input) {
        return List.of();
    }

    @Override
    public List<AiDishCandidate> recommendDishes(AiDishRecommendationInput input) {
        return List.of();
    }
}
