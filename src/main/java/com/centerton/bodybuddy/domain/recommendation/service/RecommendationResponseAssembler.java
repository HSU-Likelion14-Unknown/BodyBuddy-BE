package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.dto.NutritionRes;
import com.centerton.bodybuddy.domain.recommendation.dto.NutritionGapRes;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationDishRes;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationIngredientRes;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationRes;
import com.centerton.bodybuddy.domain.recommendation.entity.Recommendation;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDish;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationIngredient;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationDishRepository;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationIngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RecommendationResponseAssembler {

    private final RecommendationIngredientRepository ingredientRepository;
    private final RecommendationDishRepository dishRepository;

    public RecommendationRes assemble(Recommendation recommendation) {
        List<RecommendationIngredient> ingredients = ingredientRepository
                .findAllByRecommendationRecommendationIdOrderByRankOrderAsc(
                        recommendation.getRecommendationId()
                );
        Map<String, List<RecommendationDish>> dishesByIngredient = new HashMap<>();
        dishRepository.findAllForRecommendation(recommendation.getRecommendationId())
                .forEach(dish -> dishesByIngredient.computeIfAbsent(
                        dish.getIngredient().getIngredientId(),
                        ignored -> new ArrayList<>()
                ).add(dish));

        return RecommendationRes.builder()
                .recommendationId(recommendation.getRecommendationId())
                .status(recommendation.getStatus())
                .targetNutrient(recommendation.getTargetNutrient())
                .noRecommendationReason(recommendation.getNoRecommendationReason())
                .ingredients(ingredients.stream()
                        .map(ingredient -> ingredientResponse(
                                ingredient,
                                dishesByIngredient.getOrDefault(
                                        ingredient.getIngredientId(),
                                        List.of()
                                )
                        ))
                        .toList())
                .dailyNutrition(NutritionRes.from(recommendation.getDailyNutrition()))
                .nutrientGap(NutritionGapRes.from(recommendation.getNutrientGap()))
                .build();
    }

    private RecommendationIngredientRes ingredientResponse(
            RecommendationIngredient ingredient,
            List<RecommendationDish> dishes
    ) {
        return RecommendationIngredientRes.builder()
                .ingredientId(ingredient.getIngredientId())
                .foodId(ingredient.getFood().getFoodId())
                .ingredientName(ingredient.getIngredientName())
                .reason(ingredient.getReason())
                .dishes(dishes.stream().map(this::dishResponse).toList())
                .build();
    }

    private RecommendationDishRes dishResponse(RecommendationDish dish) {
        return RecommendationDishRes.builder()
                .dishId(dish.getRecommendationDishId())
                .foodId(dish.getFood() == null ? null : dish.getFood().getFoodId())
                .dishName(dish.getDishName())
                .rank(dish.getRankOrder())
                .build();
    }
}
