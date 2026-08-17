package com.centerton.bodybuddy.domain.recommendation.dto;

import com.centerton.bodybuddy.domain.meal.dto.NutritionRes;
import com.centerton.bodybuddy.domain.recommendation.entity.NoRecommendationReason;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationStatus;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecommendationRes {
    private String recommendationId;
    private RecommendationStatus status;
    private TargetNutrient targetNutrient;
    private NoRecommendationReason noRecommendationReason;
    private List<RecommendationIngredientRes> ingredients;
    private NutritionRes dailyNutrition;
    private NutritionGapRes nutrientGap;
}
