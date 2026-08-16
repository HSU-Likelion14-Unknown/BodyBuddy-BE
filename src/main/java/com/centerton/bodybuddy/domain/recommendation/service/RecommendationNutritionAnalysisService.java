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

import java.time.LocalDate;
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
        KdrReferenceValues reference = referenceProvider.referenceFor(user, date);
        NutritionValues dailyNutrition = dailyNutritionService.sumForDate(user.getUserId(), date);
        NutritionGapResult gap = gapCalculator.calculate(reference, dailyNutrition);
        List<RankedIngredient> ingredients = ingredientRankingService.rank(user, gap, limit);
        return new RecommendationNutritionAnalysis(gap, ingredients);
    }
}
