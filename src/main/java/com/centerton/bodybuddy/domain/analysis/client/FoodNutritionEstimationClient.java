package com.centerton.bodybuddy.domain.analysis.client;

import java.util.Optional;

public interface FoodNutritionEstimationClient {

    Optional<FoodNutritionEstimationResponse> estimate(FoodNutritionEstimationInput input);
}
