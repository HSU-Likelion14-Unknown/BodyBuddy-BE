package com.centerton.bodybuddy.domain.analysis.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(
        name = "bodybuddy.food-nutrition-estimation.provider",
        havingValue = "fake",
        matchIfMissing = true
)
public class NoOpFoodNutritionEstimationClient implements FoodNutritionEstimationClient {

    @Override
    public Optional<FoodNutritionEstimationResponse> estimate(FoodNutritionEstimationInput input) {
        return Optional.empty();
    }
}
