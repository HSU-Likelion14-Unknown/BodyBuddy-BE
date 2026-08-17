package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.recommendation.model.IngredientDishRecommendation;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationNutritionAnalysis;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationPlan;
import com.centerton.bodybuddy.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationPlanningService {

    private static final int MAX_RECOMMENDED_INGREDIENTS = 3;

    private final RecommendationNutritionAnalysisService nutritionAnalysisService;
    private final IngredientDishMappingService dishMappingService;

    @Transactional(readOnly = true)
    public RecommendationPlan plan(User user, LocalDate date, int ingredientLimit) {
        int safeLimit = Math.max(0, Math.min(ingredientLimit, MAX_RECOMMENDED_INGREDIENTS));
        List<String> mappableFoodIds = safeLimit == 0
                ? List.of()
                : dishMappingService.findMappableIngredientFoodIds();
        RecommendationNutritionAnalysis analysis = nutritionAnalysisService.analyzeMappable(
                user,
                date,
                mappableFoodIds
        );
        List<IngredientDishRecommendation> ingredients = dishMappingService.map(
                user,
                analysis.ingredients(),
                safeLimit
        );
        return new RecommendationPlan(analysis.nutritionGap(), ingredients);
    }
}
