package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.service.FoodNameNormalizer;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.client.AiDishCandidate;
import com.centerton.bodybuddy.domain.recommendation.client.AiIngredientCandidate;
import com.centerton.bodybuddy.domain.recommendation.client.AiIngredientRecommendationClient;
import com.centerton.bodybuddy.domain.recommendation.client.AiIngredientRecommendationInput;
import com.centerton.bodybuddy.domain.recommendation.config.RecommendationPolicyProperties;
import com.centerton.bodybuddy.domain.recommendation.entity.DishTemplate;
import com.centerton.bodybuddy.domain.recommendation.model.IngredientDishRecommendation;
import com.centerton.bodybuddy.domain.recommendation.model.NutrientGap;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendedDish;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import com.centerton.bodybuddy.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiIngredientFallbackService {

    private static final int SCORE_SCALE = 8;

    private final AiIngredientRecommendationClient client;
    private final IngredientSafetyPolicy ingredientSafetyPolicy;
    private final DishSafetyPolicy dishSafetyPolicy;
    private final RecommendationPolicyProperties properties;

    public List<IngredientDishRecommendation> recommend(
            User user,
            NutritionGapResult gapResult,
            int requestedCount,
            Collection<String> excludedIngredientNames
    ) {
        if (user == null || gapResult == null || requestedCount <= 0
                || gapResult.target().isEmpty()
                || !ingredientSafetyPolicy.canEvaluate(user.getAllergyCodes())) {
            return List.of();
        }

        Set<String> excluded = normalizedNames(excludedIngredientNames);
        AiIngredientRecommendationInput input = new AiIngredientRecommendationInput(
                gapResult.target().orElseThrow(),
                gapResult.reference(),
                gapResult,
                requestedCount,
                properties.getMinimumTargetCoveragePercent(),
                user.getAllergyCodes(),
                user.getDislikedFoods(),
                List.copyOf(excluded)
        );

        List<IngredientDishRecommendation> result = new ArrayList<>();
        List<AiIngredientCandidate> candidates;
        try {
            candidates = client.recommend(input);
        } catch (RuntimeException exception) {
            log.warn("AI 원재료 보완 후보 조회에 실패했습니다.", exception);
            return List.of();
        }
        for (AiIngredientCandidate candidate : candidates) {
            IngredientDishRecommendation validated = validateCandidate(
                    user,
                    gapResult,
                    candidate,
                    excluded,
                    result.size() + 1
            );
            if (validated == null) {
                continue;
            }
            result.add(validated);
            excluded.add(FoodNameNormalizer.normalizeLookupName(
                    validated.rankedIngredient().ingredientName()
            ));
            if (result.size() == requestedCount) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private IngredientDishRecommendation validateCandidate(
            User user,
            NutritionGapResult gapResult,
            AiIngredientCandidate candidate,
            Set<String> excluded,
            int rank
    ) {
        if (candidate == null || candidate.ingredientName() == null
                || candidate.nutritionPer100g() == null) {
            return null;
        }
        String normalizedName = FoodNameNormalizer.normalizeLookupName(
                candidate.ingredientName()
        );
        if (normalizedName.isBlank() || excluded.contains(normalizedName)
                || !ingredientSafetyPolicy.areAllergensCompatible(
                        user.getAllergyCodes(),
                        candidate.allergenCodes()
                )) {
            return null;
        }
        Food transientFood = Food.builder()
                .canonicalName(candidate.ingredientName())
                .ingredientName(candidate.ingredientName())
                .build();
        if (!ingredientSafetyPolicy.isAllowed(
                transientFood,
                user.getAllergyCodes(),
                user.getDislikedFoods()
        )) {
            return null;
        }

        TargetNutrient target = gapResult.target().orElseThrow();
        BigDecimal targetAmount = target.amountFrom(candidate.nutritionPer100g());
        BigDecimal referenceAmount = gapResult.reference().amountOf(target);
        if (targetAmount == null || targetAmount.signum() <= 0
                || referenceAmount == null || referenceAmount.signum() <= 0) {
            return null;
        }
        BigDecimal targetCoverage = targetAmount.divide(
                referenceAmount,
                SCORE_SCALE,
                RoundingMode.HALF_UP
        );
        if (targetCoverage.compareTo(properties.minimumTargetCoverageRatio()) < 0) {
            return null;
        }

        List<RecommendedDish> dishes = safeDishes(user, candidate.dishes());
        if (dishes.size() < 2) {
            return null;
        }
        RankedIngredient ranked = new RankedIngredient(
                null,
                candidate.ingredientName().trim(),
                rank,
                target,
                targetAmount,
                targetCoverage,
                overallCoverage(candidate.nutritionPer100g(), gapResult),
                candidate.nutritionPer100g()
        );
        return new IngredientDishRecommendation(ranked, dishes);
    }

    private List<RecommendedDish> safeDishes(User user, List<AiDishCandidate> candidates) {
        if (candidates == null) {
            return List.of();
        }
        Set<String> names = new HashSet<>();
        List<RecommendedDish> result = candidates.stream()
                .filter(java.util.Objects::nonNull)
                .filter(candidate -> candidate.dishName() != null)
                .filter(candidate -> names.add(FoodNameNormalizer.normalizeLookupName(
                        candidate.dishName()
                )))
                .filter(candidate -> dishSafetyPolicy.isAllowed(
                        user,
                        DishTemplate.builder()
                                .dishName(candidate.dishName())
                                .normalizedName(FoodNameNormalizer.normalizeLookupName(
                                        candidate.dishName()
                                ))
                                .ingredientNames(candidate.ingredientNames())
                                .allergenCodes(candidate.allergenCodes())
                                .active(true)
                                .build()
                ))
                .limit(3)
                .map(candidate -> new RecommendedDish(
                        null,
                        null,
                        candidate.dishName().trim(),
                        0
                ))
                .toList();
        List<RecommendedDish> ranked = new ArrayList<>();
        for (int index = 0; index < result.size(); index++) {
            RecommendedDish dish = result.get(index);
            ranked.add(new RecommendedDish(
                    dish.dishId(),
                    dish.foodId(),
                    dish.dishName(),
                    index + 1
            ));
        }
        return List.copyOf(ranked);
    }

    private BigDecimal overallCoverage(NutritionValues nutrition,
                                       NutritionGapResult gapResult) {
        BigDecimal score = BigDecimal.ZERO;
        for (TargetNutrient nutrient : TargetNutrient.values()) {
            NutrientGap gap = gapResult.gapOf(nutrient);
            BigDecimal amount = nutrient.amountFrom(nutrition);
            if (gap == null || gap.gapAmount().signum() <= 0
                    || amount == null || amount.signum() <= 0) {
                continue;
            }
            BigDecimal coverage = amount.divide(
                    gap.gapAmount(),
                    SCORE_SCALE,
                    RoundingMode.HALF_UP
            ).min(BigDecimal.ONE);
            score = score.add(coverage.multiply(gap.gapRatio()));
        }
        return score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    private Set<String> normalizedNames(Collection<String> names) {
        Set<String> result = new HashSet<>();
        if (names == null) {
            return result;
        }
        names.stream()
                .filter(java.util.Objects::nonNull)
                .map(FoodNameNormalizer::normalizeLookupName)
                .filter(name -> !name.isBlank())
                .forEach(result::add);
        return result;
    }
}
