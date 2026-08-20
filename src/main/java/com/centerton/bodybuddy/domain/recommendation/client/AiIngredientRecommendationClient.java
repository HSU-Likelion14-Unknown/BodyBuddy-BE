package com.centerton.bodybuddy.domain.recommendation.client;

import java.util.List;

public interface AiIngredientRecommendationClient {
    List<AiIngredientCandidate> recommend(AiIngredientRecommendationInput input);

    List<AiDishCandidate> recommendDishes(AiDishRecommendationInput input);
}
