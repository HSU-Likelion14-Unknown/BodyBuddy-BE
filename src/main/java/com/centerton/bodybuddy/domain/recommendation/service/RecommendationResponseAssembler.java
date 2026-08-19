package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.dto.NutritionRes;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.dto.NutrientCoverageRes;
import com.centerton.bodybuddy.domain.recommendation.dto.NutritionGapRes;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationDishRes;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationIngredientRes;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationRes;
import com.centerton.bodybuddy.domain.recommendation.entity.Recommendation;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDish;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationDishRepository;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationIngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RecommendationResponseAssembler {

    private static final BigDecimal ZERO_PERCENT = new BigDecimal("0.0");
    private static final BigDecimal MAX_PERCENT = new BigDecimal("100.0");
    private static final int PERCENT_SCALE = 1;

    private final RecommendationIngredientRepository ingredientRepository;
    private final RecommendationDishRepository dishRepository;
    private final KdrReferenceProvider kdrReferenceProvider;

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
        KdrReferenceValues reference = ingredients.isEmpty()
                ? null
                : kdrReferenceProvider.referenceFor(
                        recommendation.getUser(),
                        recommendation.getRecommendationDate()
                );

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
                                ),
                                reference
                        ))
                        .toList())
                .dailyNutrition(NutritionRes.from(recommendation.getDailyNutrition()))
                .nutrientGap(NutritionGapRes.from(recommendation.getNutrientGap()))
                .build();
    }

    private RecommendationIngredientRes ingredientResponse(
            RecommendationIngredient ingredient,
            List<RecommendationDish> dishes,
            KdrReferenceValues reference
    ) {
        return RecommendationIngredientRes.builder()
                .ingredientId(ingredient.getIngredientId())
                .foodId(ingredient.getFood().getFoodId())
                .ingredientName(ingredient.getIngredientName())
                .reason(ingredient.getReason())
                .nutrientCoverages(nutrientCoverages(
                        ingredient.getNutritionSnapshot(),
                        reference
                ))
                .dishes(dishes.stream().map(this::dishResponse).toList())
                .build();
    }

    private List<NutrientCoverageRes> nutrientCoverages(
            NutritionValues ingredientNutrition,
            KdrReferenceValues reference
    ) {
        return Arrays.stream(TargetNutrient.values())
                .map(nutrient -> NutrientCoverageRes.builder()
                        .nutrient(nutrient)
                        .coveragePercent(coveragePercent(
                                nutrient.amountFrom(ingredientNutrition),
                                reference.amountOf(nutrient)
                        ))
                        .build())
                .sorted(Comparator.comparing(
                                NutrientCoverageRes::getCoveragePercent,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(coverage -> coverage.getNutrient().ordinal()))
                .toList();
    }

    private BigDecimal coveragePercent(BigDecimal nutrientAmount,
                                       BigDecimal dailyReferenceAmount) {
        if (nutrientAmount == null || nutrientAmount.signum() <= 0
                || dailyReferenceAmount == null || dailyReferenceAmount.signum() <= 0) {
            return ZERO_PERCENT;
        }
        return nutrientAmount.multiply(BigDecimal.valueOf(100))
                .divide(dailyReferenceAmount, PERCENT_SCALE, RoundingMode.HALF_UP)
                .min(MAX_PERCENT);
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
