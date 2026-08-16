package com.centerton.bodybuddy.domain.recommendation.model;

public record RecommendedDish(
        String dishId,
        String foodId,
        String dishName,
        int rank
) {
}
