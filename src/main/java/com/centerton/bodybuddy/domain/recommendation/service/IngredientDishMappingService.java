package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.food.service.FoodNameNormalizer;
import com.centerton.bodybuddy.domain.recommendation.entity.DishTemplate;
import com.centerton.bodybuddy.domain.recommendation.entity.IngredientDishMapping;
import com.centerton.bodybuddy.domain.recommendation.model.IngredientDishRecommendation;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendedDish;
import com.centerton.bodybuddy.domain.recommendation.repository.IngredientDishMappingRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IngredientDishMappingService {

    private static final int MIN_DISHES_PER_INGREDIENT = 2;
    private static final int MAX_DISHES_PER_INGREDIENT = 3;
    private static final int MAX_RECOMMENDED_INGREDIENTS = 3;

    private final IngredientDishMappingRepository mappingRepository;
    private final DishSafetyPolicy dishSafetyPolicy;

    @Transactional(readOnly = true)
    public List<IngredientDishRecommendation> map(User user,
                                                  List<RankedIngredient> rankedIngredients,
                                                  int ingredientLimit) {
        if (user == null || rankedIngredients == null
                || rankedIngredients.isEmpty() || ingredientLimit <= 0) {
            return List.of();
        }
        int safeIngredientLimit = Math.min(
                ingredientLimit,
                MAX_RECOMMENDED_INGREDIENTS
        );
        List<RankedIngredient> candidates = rankedIngredients.stream()
                .filter(java.util.Objects::nonNull)
                .toList();

        List<String> foodIds = candidates.stream()
                .map(RankedIngredient::foodId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (foodIds.isEmpty()) {
            return List.of();
        }

        Map<String, List<IngredientDishMapping>> mappingsByIngredient = new HashMap<>();
        mappingRepository.findActiveMappings(foodIds).forEach(mapping ->
                mappingsByIngredient.computeIfAbsent(
                        mapping.getIngredientFood().getFoodId(),
                        ignored -> new ArrayList<>()
                ).add(mapping)
        );

        List<IngredientDishRecommendation> result = new ArrayList<>();
        for (RankedIngredient ingredient : candidates) {
            List<RecommendedDish> dishes = safeDishes(
                    user,
                    mappingsByIngredient.getOrDefault(ingredient.foodId(), List.of())
            );
            if (dishes.size() < MIN_DISHES_PER_INGREDIENT) {
                continue;
            }
            result.add(new IngredientDishRecommendation(ingredient, dishes));
            if (result.size() == safeIngredientLimit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private List<RecommendedDish> safeDishes(User user,
                                             List<IngredientDishMapping> mappings) {
        Set<String> usedDishNames = new HashSet<>();
        List<RecommendedDish> result = new ArrayList<>();
        for (IngredientDishMapping mapping : mappings) {
            DishTemplate dish = mapping.getDish();
            String normalizedName = FoodNameNormalizer.normalizeLookupName(
                    dish.getDishName()
            );
            if (normalizedName.isBlank()
                    || !dishSafetyPolicy.isAllowed(user, dish)
                    || !usedDishNames.add(normalizedName)) {
                continue;
            }
            result.add(new RecommendedDish(
                    dish.getDishId(),
                    dish.getFood() == null ? null : dish.getFood().getFoodId(),
                    dish.getDishName(),
                    result.size() + 1
            ));
            if (result.size() == MAX_DISHES_PER_INGREDIENT) {
                break;
            }
        }
        return List.copyOf(result);
    }
}
