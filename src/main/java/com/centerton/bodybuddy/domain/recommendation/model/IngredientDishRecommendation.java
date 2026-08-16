package com.centerton.bodybuddy.domain.recommendation.model;

import java.util.List;

public record IngredientDishRecommendation(
        RankedIngredient rankedIngredient,
        List<RecommendedDish> dishes
) {

    public IngredientDishRecommendation {
        dishes = List.copyOf(dishes);
    }
}
