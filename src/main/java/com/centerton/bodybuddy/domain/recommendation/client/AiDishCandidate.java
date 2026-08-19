package com.centerton.bodybuddy.domain.recommendation.client;

import java.util.List;

public record AiDishCandidate(
        String dishName,
        List<String> ingredientNames,
        List<String> allergenCodes
) {
    public AiDishCandidate {
        ingredientNames = ingredientNames == null ? List.of() : List.copyOf(ingredientNames);
        allergenCodes = allergenCodes == null ? List.of() : List.copyOf(allergenCodes);
    }
}
