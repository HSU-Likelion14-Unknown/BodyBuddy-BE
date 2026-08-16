package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.entity.FoodNutrition;
import com.centerton.bodybuddy.domain.food.repository.FoodNutritionRepository;
import com.centerton.bodybuddy.domain.food.service.FoodNameNormalizer;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutrientGap;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import com.centerton.bodybuddy.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IngredientRankingService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int SCORE_SCALE = 8;

    private final FoodNutritionRepository foodNutritionRepository;
    private final IngredientSafetyPolicy safetyPolicy;

    @Transactional(readOnly = true)
    public List<RankedIngredient> rank(User user, NutritionGapResult gapResult, int limit) {
        if (limit <= 0 || gapResult.target().isEmpty()) {
            return List.of();
        }
        if (!safetyPolicy.canEvaluate(user.getAllergyCodes())) {
            return List.of();
        }

        TargetNutrient target = gapResult.target().orElseThrow();
        List<CandidateScore> scores = foodNutritionRepository.findRecommendationCandidates()
                .stream()
                .filter(this::eligibleCandidate)
                .filter(candidate -> safetyPolicy.isAllowed(
                        candidate.getFood(),
                        user.getAllergyCodes(),
                        user.getDislikedFoods()
                ))
                .map(candidate -> score(candidate, target, gapResult))
                .filter(java.util.Objects::nonNull)
                .sorted(candidateComparator())
                .toList();

        Set<String> usedIngredients = new HashSet<>();
        List<RankedIngredient> result = new ArrayList<>();
        for (CandidateScore score : scores) {
            String normalizedIngredient = FoodNameNormalizer.normalizeLookupName(
                    score.nutrition().getFood().getIngredientName()
            );
            if (!usedIngredients.add(normalizedIngredient)) {
                continue;
            }
            result.add(score.toRankedIngredient(result.size() + 1, target));
            if (result.size() == limit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private boolean eligibleCandidate(FoodNutrition candidate) {
        Food food = candidate.getFood();
        return food != null
                && food.isActive()
                && food.isRecommendationCandidate()
                && "INGREDIENT".equals(food.getFoodType())
                && food.getIngredientName() != null
                && !food.getIngredientName().isBlank()
                && candidate.getNutrition() != null
                && candidate.getReferenceAmount() != null
                && candidate.getReferenceAmount().signum() > 0
                && "g".equalsIgnoreCase(candidate.getReferenceUnit());
    }

    private CandidateScore score(FoodNutrition candidate, TargetNutrient target,
                                 NutritionGapResult gapResult) {
        NutritionValues per100g = scaleTo100g(candidate);
        BigDecimal targetAmount = target.amountFrom(per100g);
        if (targetAmount == null || targetAmount.signum() <= 0) {
            return null;
        }
        BigDecimal targetCoverage = targetAmount.divide(
                gapResult.reference().amountOf(target),
                SCORE_SCALE,
                RoundingMode.HALF_UP
        );
        BigDecimal overallScore = overallCoverage(per100g, gapResult);
        return new CandidateScore(
                candidate,
                per100g,
                targetAmount,
                targetCoverage,
                overallScore
        );
    }

    private BigDecimal overallCoverage(NutritionValues per100g,
                                       NutritionGapResult gapResult) {
        BigDecimal score = BigDecimal.ZERO;
        for (TargetNutrient nutrient : TargetNutrient.values()) {
            NutrientGap gap = gapResult.gapOf(nutrient);
            BigDecimal amount = nutrient.amountFrom(per100g);
            if (gap.gapAmount().signum() == 0 || amount == null || amount.signum() <= 0) {
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

    private NutritionValues scaleTo100g(FoodNutrition candidate) {
        BigDecimal ratio = ONE_HUNDRED.divide(
                candidate.getReferenceAmount(),
                SCORE_SCALE,
                RoundingMode.HALF_UP
        );
        NutritionValues values = candidate.getNutrition();
        return NutritionValues.builder()
                .caloriesKcal(scale(values.getCaloriesKcal(), ratio))
                .carbohydrateG(scale(values.getCarbohydrateG(), ratio))
                .proteinG(scale(values.getProteinG(), ratio))
                .fatG(scale(values.getFatG(), ratio))
                .fiberG(scale(values.getFiberG(), ratio))
                .sodiumMg(scale(values.getSodiumMg(), ratio))
                .calciumMg(scale(values.getCalciumMg(), ratio))
                .ironMg(scale(values.getIronMg(), ratio))
                .potassiumMg(scale(values.getPotassiumMg(), ratio))
                .vitaminAMcgRae(scale(values.getVitaminAMcgRae(), ratio))
                .vitaminCMg(scale(values.getVitaminCMg(), ratio))
                .build();
    }

    private BigDecimal scale(BigDecimal value, BigDecimal ratio) {
        return value == null
                ? null
                : value.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private Comparator<CandidateScore> candidateComparator() {
        return Comparator.comparing(CandidateScore::targetAmount).reversed()
                .thenComparing(CandidateScore::overallScore, Comparator.reverseOrder())
                .thenComparing(score -> score.nutrition().getFood().getIngredientName())
                .thenComparing(score -> score.nutrition().getFood().getFoodId());
    }

    private record CandidateScore(
            FoodNutrition nutrition,
            NutritionValues nutritionPer100g,
            BigDecimal targetAmount,
            BigDecimal targetCoverage,
            BigDecimal overallScore
    ) {
        private RankedIngredient toRankedIngredient(int rank, TargetNutrient target) {
            return new RankedIngredient(
                    nutrition.getFood().getFoodId(),
                    nutrition.getFood().getIngredientName(),
                    rank,
                    target,
                    targetAmount,
                    targetCoverage,
                    overallScore,
                    nutritionPer100g
            );
        }
    }
}
