package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationNutritionAnalysis;
import com.centerton.bodybuddy.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationNutritionAnalysisServiceTest {

    @Mock
    private KdrReferenceProvider referenceProvider;

    @Mock
    private DailyNutritionService dailyNutritionService;

    @Mock
    private NutrientGapCalculator gapCalculator;

    @Mock
    private IngredientRankingService ingredientRankingService;

    @InjectMocks
    private RecommendationNutritionAnalysisService analysisService;

    @Test
    void combinesReferenceDailyNutritionGapAndIngredientRanking() {
        User user = User.builder().userId("user-id").build();
        LocalDate date = LocalDate.of(2026, 8, 16);
        KdrReferenceValues reference = new KdrReferenceValues(
                value("65"), value("30"), value("800"), value("8"),
                value("3500"), value("800"), value("100")
        );
        NutritionValues dailyNutrition = NutritionValues.builder()
                .proteinG(value("30"))
                .build();
        NutritionGapResult gap = new NutritionGapResult(
                reference,
                dailyNutrition,
                Map.of(),
                null
        );

        when(referenceProvider.referenceFor(user, date)).thenReturn(reference);
        when(dailyNutritionService.sumForDate("user-id", date)).thenReturn(dailyNutrition);
        when(gapCalculator.calculate(reference, dailyNutrition)).thenReturn(gap);
        when(ingredientRankingService.rank(user, gap, 5)).thenReturn(List.of());

        RecommendationNutritionAnalysis result = analysisService.analyze(user, date, 5);

        assertThat(result.nutritionGap()).isSameAs(gap);
        assertThat(result.ingredients()).isEmpty();
        verify(ingredientRankingService).rank(user, gap, 5);
    }

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }
}
