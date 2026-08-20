package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.recommendation.config.RecommendationPolicyProperties;
import com.centerton.bodybuddy.domain.recommendation.model.IngredientDishRecommendation;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationNutritionAnalysis;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationPlan;
import com.centerton.bodybuddy.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecommendationPlanningService {

    private static final int MAX_RECOMMENDED_INGREDIENTS = 3;

    private final RecommendationNutritionAnalysisService nutritionAnalysisService;
    private final IngredientDishMappingService dishMappingService;
    private final AiIngredientFallbackService aiFallbackService;
    private final RecommendationPolicyProperties properties;

    @Transactional(readOnly = true)
    public RecommendationPlan plan(User user, LocalDate date, int ingredientLimit) {
        return plan(user, date, ingredientLimit, List.of());
    }

    @Transactional(readOnly = true)
    public RecommendationPlan plan(User user, LocalDate date, int ingredientLimit,
                                   Collection<String> excludedIngredientNames) {
        int safeLimit = Math.max(0, Math.min(ingredientLimit, MAX_RECOMMENDED_INGREDIENTS));
        List<String> mappableFoodIds = safeLimit == 0
                ? List.of()
                : dishMappingService.findMappableIngredientFoodIds();
        RecommendationNutritionAnalysis analysis = nutritionAnalysisService.analyzeMappable(
                user,
                date,
                mappableFoodIds,
                excludedIngredientNames,
                properties.minimumTargetCoverageRatio()
        );
        List<IngredientDishRecommendation> catalogIngredients = dishMappingService.map(
                user,
                analysis.ingredients(),
                safeLimit
        );
        List<IngredientDishRecommendation> combined = new ArrayList<>(catalogIngredients);
        if (combined.size() < safeLimit && analysis.nutritionGap().target().isPresent()) {
            Set<String> aiExclusions = new HashSet<>();
            if (excludedIngredientNames != null) {
                aiExclusions.addAll(excludedIngredientNames);
            }
            catalogIngredients.stream()
                    .map(IngredientDishRecommendation::rankedIngredient)
                    .map(RankedIngredient::ingredientName)
                    .forEach(aiExclusions::add);
            combined.addAll(aiFallbackService.recommend(
                    user,
                    analysis.nutritionGap(),
                    safeLimit - combined.size(),
                    aiExclusions
            ));
        }
        if (combined.size() != safeLimit) {
            return new RecommendationPlan(analysis.nutritionGap(), List.of());
        }
        List<IngredientDishRecommendation> ranked = new ArrayList<>();
        for (int index = 0; index < combined.size(); index++) {
            IngredientDishRecommendation ingredient = combined.get(index);
            RankedIngredient current = ingredient.rankedIngredient();
            ranked.add(new IngredientDishRecommendation(
                    new RankedIngredient(
                            current.foodId(),
                            current.ingredientName(),
                            index + 1,
                            current.targetNutrient(),
                            current.targetAmountPerServing(),
                            current.dailyTargetCoverageRatio(),
                            current.overallDeficiencyCoverageScore(),
                            current.nutritionPerServing()
                    ),
                    ingredient.dishes()
            ));
        }
        return new RecommendationPlan(analysis.nutritionGap(), ranked);
    }
}
