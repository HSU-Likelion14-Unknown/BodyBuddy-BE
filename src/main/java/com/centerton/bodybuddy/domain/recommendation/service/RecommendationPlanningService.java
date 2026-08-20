package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.food.service.FoodNameNormalizer;
import com.centerton.bodybuddy.domain.recommendation.config.RecommendationPolicyProperties;
import com.centerton.bodybuddy.domain.recommendation.model.IngredientDishRecommendation;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationNutritionAnalysis;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationPlan;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendedDish;
import com.centerton.bodybuddy.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private static final int CATALOG_CANDIDATE_POOL_SIZE = 12;
    private static final int MAX_AI_DISH_COMPLETION_ATTEMPTS = 4;

    private final RecommendationNutritionAnalysisService nutritionAnalysisService;
    private final IngredientDishMappingService dishMappingService;
    private final AiIngredientFallbackService aiFallbackService;
    private final RecommendationPolicyProperties properties;

    public RecommendationPlan plan(User user, LocalDate date, int ingredientLimit) {
        return plan(user, date, ingredientLimit, List.of());
    }

    public RecommendationPlan plan(User user, LocalDate date, int ingredientLimit,
                                   Collection<String> excludedIngredientNames) {
        int safeLimit = Math.max(0, Math.min(ingredientLimit, MAX_RECOMMENDED_INGREDIENTS));
        RecommendationNutritionAnalysis analysis = nutritionAnalysisService.analyze(
                user,
                date,
                safeLimit == 0 ? 0 : CATALOG_CANDIDATE_POOL_SIZE,
                excludedIngredientNames,
                properties.minimumTargetCoverageRatio()
        );
        List<IngredientDishRecommendation> combined = completeCatalogIngredients(
                user, analysis.ingredients(), safeLimit
        );
        if (combined.size() < safeLimit && analysis.nutritionGap().target().isPresent()) {
            Set<String> aiExclusions = new HashSet<>();
            if (excludedIngredientNames != null) {
                aiExclusions.addAll(excludedIngredientNames);
            }
            combined.stream()
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

    private List<IngredientDishRecommendation> completeCatalogIngredients(
            User user,
            List<RankedIngredient> candidates,
            int limit
    ) {
        if (limit <= 0 || candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        List<IngredientDishRecommendation> result = new ArrayList<>();
        int aiAttempts = 0;
        for (RankedIngredient candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            List<RecommendedDish> dishes = dishMappingService.findSafeDishes(
                    user, candidate
            );
            if (dishes.size() < 2 && aiAttempts < MAX_AI_DISH_COMPLETION_ATTEMPTS) {
                aiAttempts++;
                dishes = mergeDishes(
                        dishes,
                        aiFallbackService.completeDishes(user, candidate)
                );
            }
            if (dishes.size() < 2) {
                continue;
            }
            result.add(new IngredientDishRecommendation(candidate, dishes));
            if (result.size() == limit) {
                break;
            }
        }
        return result;
    }

    private List<RecommendedDish> mergeDishes(
            List<RecommendedDish> catalogDishes,
            List<RecommendedDish> aiDishes
    ) {
        List<RecommendedDish> merged = new ArrayList<>();
        Set<String> normalizedNames = new HashSet<>();
        addUniqueDishes(merged, normalizedNames, catalogDishes);
        addUniqueDishes(merged, normalizedNames, aiDishes);

        List<RecommendedDish> ranked = new ArrayList<>();
        for (int index = 0; index < Math.min(3, merged.size()); index++) {
            RecommendedDish dish = merged.get(index);
            ranked.add(new RecommendedDish(
                    dish.dishId(),
                    dish.foodId(),
                    dish.dishName(),
                    index + 1
            ));
        }
        return List.copyOf(ranked);
    }

    private void addUniqueDishes(
            List<RecommendedDish> target,
            Set<String> normalizedNames,
            List<RecommendedDish> candidates
    ) {
        if (candidates == null) {
            return;
        }
        for (RecommendedDish dish : candidates) {
            if (dish == null || dish.dishName() == null) {
                continue;
            }
            String normalizedName = FoodNameNormalizer.normalizeLookupName(dish.dishName());
            if (!normalizedName.isBlank() && normalizedNames.add(normalizedName)) {
                target.add(dish);
            }
        }
    }
}
