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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IngredientRankingService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int SCORE_SCALE = 8;
    private static final int CANDIDATE_QUERY_BATCH_SIZE = 100;

    private final FoodNutritionRepository foodNutritionRepository;
    private final IngredientSafetyPolicy safetyPolicy;

    @Transactional(readOnly = true)
    public List<RankedIngredient> rank(User user, NutritionGapResult gapResult, int limit) {
        if (!canRank(user, gapResult, limit)) {
            return List.of();
        }
        return rankCandidates(
                user,
                gapResult,
                limit,
                foodNutritionRepository.findRecommendationCandidates(),
                List.of(),
                BigDecimal.ZERO
        );
    }

    @Transactional(readOnly = true)
    public List<RankedIngredient> rankMappable(User user,
                                              NutritionGapResult gapResult,
                                              Collection<String> mappableFoodIds) {
        return rankMappable(
                user,
                gapResult,
                mappableFoodIds,
                List.of(),
                BigDecimal.ZERO
        );
    }

    @Transactional(readOnly = true)
    public List<RankedIngredient> rankMappable(
            User user,
            NutritionGapResult gapResult,
            Collection<String> mappableFoodIds,
            Collection<String> excludedIngredientNames,
            BigDecimal minimumTargetCoverageRatio
    ) {
        if (mappableFoodIds == null || mappableFoodIds.isEmpty()
                || !canRank(user, gapResult, Integer.MAX_VALUE)) {
            return List.of();
        }
        Set<String> uniqueIds = mappableFoodIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> uniqueFoodIds = new ArrayList<>(uniqueIds);
        if (uniqueFoodIds.isEmpty()) {
            return List.of();
        }
        List<FoodNutrition> candidates = new ArrayList<>();
        for (int start = 0; start < uniqueFoodIds.size(); start += CANDIDATE_QUERY_BATCH_SIZE) {
            int end = Math.min(start + CANDIDATE_QUERY_BATCH_SIZE, uniqueFoodIds.size());
            candidates.addAll(foodNutritionRepository.findRecommendationCandidatesByFoodIds(
                    uniqueFoodIds.subList(start, end)
            ));
        }
        return rankCandidates(
                user,
                gapResult,
                uniqueFoodIds.size(),
                candidates,
                excludedIngredientNames,
                minimumTargetCoverageRatio
        );
    }

    private boolean canRank(User user, NutritionGapResult gapResult, int limit) {
        return user != null
                && gapResult != null
                && limit > 0
                && gapResult.target().isPresent()
                && safetyPolicy.canEvaluate(user.getAllergyCodes());
    }

    private List<RankedIngredient> rankCandidates(User user,
                                                  NutritionGapResult gapResult,
                                                  int limit,
                                                  List<FoodNutrition> candidates,
                                                  Collection<String> excludedIngredientNames,
                                                  BigDecimal minimumTargetCoverageRatio) {
        TargetNutrient target = gapResult.target().orElseThrow();
        BigDecimal minimumCoverage = minimumTargetCoverageRatio == null
                ? BigDecimal.ZERO
                : minimumTargetCoverageRatio.max(BigDecimal.ZERO);
        List<CandidateScore> scores = candidates.stream()
                .filter(this::eligibleCandidate)
                .filter(candidate -> safetyPolicy.isAllowed(
                        candidate.getFood(),
                        user.getAllergyCodes(),
                        user.getDislikedFoods()
                ))
                .map(candidate -> score(candidate, target, gapResult))
                .filter(java.util.Objects::nonNull)
                .filter(candidate -> candidate.targetCoverage().compareTo(minimumCoverage) >= 0)
                .sorted(candidateComparator())
                .toList();

        Set<String> usedIngredients = new HashSet<>();
        if (excludedIngredientNames != null) {
            excludedIngredientNames.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(FoodNameNormalizer::normalizeLookupName)
                    .filter(name -> !name.isBlank())
                    .forEach(usedIngredients::add);
        }
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
            if (gap == null
                    || gap.gapAmount().signum() == 0
                    || amount == null
                    || amount.signum() <= 0) {
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
