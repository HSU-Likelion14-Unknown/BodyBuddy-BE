package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationNutritionAnalysis;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationNutritionAnalysisService {

    private final KdrReferenceProvider referenceProvider;
    private final DailyNutritionService dailyNutritionService;
    private final NutrientGapCalculator gapCalculator;
    private final IngredientRankingService ingredientRankingService;

    @Transactional(readOnly = true)
    public RecommendationNutritionAnalysis analyze(User user, LocalDate date, int limit) {
        NutritionGapResult gap = calculateGap(user, date);
        List<RankedIngredient> ingredients = ingredientRankingService.rank(user, gap, limit);
        return new RecommendationNutritionAnalysis(gap, ingredients);
    }

    @Transactional(readOnly = true)
    public RecommendationNutritionAnalysis analyzeMappable(
            User user,
            LocalDate date,
            Collection<String> mappableFoodIds
    ) {
        return analyzeMappable(
                user,
                date,
                mappableFoodIds,
                List.of(),
                BigDecimal.ZERO
        );
    }

    @Transactional(readOnly = true)
    public RecommendationNutritionAnalysis analyzeMappable(
            User user,
            LocalDate date,
            Collection<String> mappableFoodIds,
            Collection<String> excludedIngredientNames,
            BigDecimal minimumTargetCoverageRatio
    ) {
        NutritionGapResult gap = calculateGap(user, date);
        List<RankedIngredient> ingredients = ingredientRankingService.rankMappable(
                user,
                gap,
                mappableFoodIds,
                excludedIngredientNames,
                minimumTargetCoverageRatio
        );
        return new RecommendationNutritionAnalysis(gap, ingredients);
    }

    private NutritionGapResult calculateGap(User user, LocalDate date) {
        KdrReferenceValues reference = referenceProvider.referenceFor(user, date);
        NutritionValues dailyNutrition = dailyNutritionService.sumForDate(user.getUserId(), date);
        return gapCalculator.calculate(reference, dailyNutrition);
    }
}
