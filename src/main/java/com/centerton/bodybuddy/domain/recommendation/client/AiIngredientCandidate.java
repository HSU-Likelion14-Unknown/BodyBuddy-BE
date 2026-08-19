package com.centerton.bodybuddy.domain.recommendation.client;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;

import java.util.List;

public record AiIngredientCandidate(
        String ingredientName,
        List<String> allergenCodes,
        NutritionValues nutritionPer100g,
        List<AiDishCandidate> dishes
) {
    public AiIngredientCandidate {
        allergenCodes = allergenCodes == null ? List.of() : List.copyOf(allergenCodes);
        dishes = dishes == null ? List.of() : List.copyOf(dishes);
    }
}
