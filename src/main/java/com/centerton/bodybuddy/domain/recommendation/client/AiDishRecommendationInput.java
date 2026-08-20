package com.centerton.bodybuddy.domain.recommendation.client;

import java.util.List;

public record AiDishRecommendationInput(
        String ingredientName,
        List<String> allergyCodes,
        List<String> dislikedFoods
) {
    public AiDishRecommendationInput {
        allergyCodes = allergyCodes == null ? List.of() : List.copyOf(allergyCodes);
        dislikedFoods = dislikedFoods == null ? List.of() : List.copyOf(dislikedFoods);
    }
}
