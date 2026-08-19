package com.centerton.bodybuddy.domain.recommendation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecommendationIngredientRes {
    private String ingredientId;
    private String foodId;
    private String ingredientName;
    private String reason;
    private List<NutrientCoverageRes> nutrientCoverages;
    private List<RecommendationDishRes> dishes;
}
