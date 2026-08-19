package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationNutritionAnalysis;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationPlan;
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
class RecommendationPlanningServiceTest {

    @Mock
    private RecommendationNutritionAnalysisService nutritionAnalysisService;

    @Mock
    private IngredientDishMappingService dishMappingService;

    @Mock
    private AiIngredientFallbackService aiFallbackService;

    @Mock
    private com.centerton.bodybuddy.domain.recommendation.config.RecommendationPolicyProperties properties;

    @InjectMocks
    private RecommendationPlanningService planningService;

    @Test
    void returnsNoCandidatesWhenDbAndAiCannotFillExactlyTwoIngredients() {
        User user = User.builder().userId("user-id").build();
        LocalDate date = LocalDate.of(2026, 8, 16);
        NutritionGapResult gap = gap();
        RecommendationNutritionAnalysis analysis = new RecommendationNutritionAnalysis(
                gap,
                List.of()
        );
        List<String> mappableFoodIds = List.of("spinach", "broccoli");
        when(dishMappingService.findMappableIngredientFoodIds())
                .thenReturn(mappableFoodIds);
        when(properties.minimumTargetCoverageRatio()).thenReturn(value("0.20"));
        when(nutritionAnalysisService.analyzeMappable(
                user, date, mappableFoodIds, List.of(), value("0.20")))
                .thenReturn(analysis);
        when(dishMappingService.map(user, List.of(), 2)).thenReturn(List.of());
        RecommendationPlan result = planningService.plan(user, date, 2);

        assertThat(result.nutritionGap()).isSameAs(gap);
        assertThat(result.ingredients()).isEmpty();
        verify(nutritionAnalysisService).analyzeMappable(
                user, date, mappableFoodIds, List.of(), value("0.20"));
        verify(dishMappingService).map(user, List.of(), 2);
    }

    private NutritionGapResult gap() {
        KdrReferenceValues reference = new KdrReferenceValues(
                value("65"), value("30"), value("800"), value("8"),
                value("3500"), value("800"), value("100")
        );
        return new NutritionGapResult(
                reference,
                NutritionValues.builder().build(),
                Map.of(),
                null
        );
    }

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }
}
