package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.config.RecommendationPolicyProperties;
import com.centerton.bodybuddy.domain.recommendation.model.IngredientDishRecommendation;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutrientGap;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationNutritionAnalysis;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationPlan;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendedDish;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import com.centerton.bodybuddy.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationPlanningServiceTest {

    @Mock private RecommendationNutritionAnalysisService nutritionAnalysisService;
    @Mock private IngredientDishMappingService dishMappingService;
    @Mock private AiIngredientFallbackService aiFallbackService;
    @Mock private RecommendationPolicyProperties properties;

    @InjectMocks
    private RecommendationPlanningService planningService;

    @Test
    void fillsMissingCatalogDishesWithAiAndKeepsNutritionRankingOrder() {
        User user = User.builder().userId("user-id").build();
        LocalDate date = LocalDate.of(2026, 8, 16);
        NutritionGapResult gap = gap(TargetNutrient.IRON);
        RankedIngredient spinach = ranked("spinach", "시금치", 1);
        RankedIngredient broccoli = ranked("broccoli", "브로콜리", 2);
        when(properties.minimumTargetCoverageRatio()).thenReturn(value("0.20"));
        when(nutritionAnalysisService.analyze(
                user, date, 12, List.of(), value("0.20")))
                .thenReturn(new RecommendationNutritionAnalysis(
                        gap, List.of(spinach, broccoli)
                ));
        when(dishMappingService.findSafeDishes(user, spinach)).thenReturn(List.of());
        when(aiFallbackService.completeDishes(user, spinach))
                .thenReturn(dishes("시금치"));
        when(dishMappingService.findSafeDishes(user, broccoli))
                .thenReturn(dishes("브로콜리"));

        RecommendationPlan result = planningService.plan(user, date, 2);

        assertThat(result.ingredients())
                .extracting(item -> item.rankedIngredient().ingredientName())
                .containsExactly("시금치", "브로콜리");
        assertThat(result.ingredients())
                .extracting(item -> item.rankedIngredient().rank())
                .containsExactly(1, 2);
        verify(aiFallbackService).completeDishes(user, spinach);
        verify(aiFallbackService, never()).recommend(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                anyCollection()
        );
    }

    @Test
    void preservesSingleCatalogDishAndAddsOnlyUniqueAiDishes() {
        User user = User.builder().userId("user-id").build();
        LocalDate date = LocalDate.of(2026, 8, 16);
        NutritionGapResult gap = gap(TargetNutrient.IRON);
        RankedIngredient spinach = ranked("spinach", "시금치", 1);
        RecommendedDish catalogDish = new RecommendedDish(
                "dish-id", "food-id", "시금치무침", 1
        );
        when(properties.minimumTargetCoverageRatio()).thenReturn(value("0.20"));
        when(nutritionAnalysisService.analyze(
                user, date, 12, List.of(), value("0.20")))
                .thenReturn(new RecommendationNutritionAnalysis(gap, List.of(spinach)));
        when(dishMappingService.findSafeDishes(user, spinach))
                .thenReturn(List.of(catalogDish));
        when(aiFallbackService.completeDishes(user, spinach)).thenReturn(List.of(
                new RecommendedDish(null, null, "시금치 무침", 1),
                new RecommendedDish(null, null, "시금치국", 2)
        ));

        RecommendationPlan result = planningService.plan(user, date, 1);

        assertThat(result.ingredients()).hasSize(1);
        assertThat(result.ingredients().getFirst().dishes())
                .extracting(RecommendedDish::dishName)
                .containsExactly("시금치무침", "시금치국");
        assertThat(result.ingredients().getFirst().dishes().getFirst().dishId())
                .isEqualTo("dish-id");
        assertThat(result.ingredients().getFirst().dishes())
                .extracting(RecommendedDish::rank)
                .containsExactly(1, 2);
    }

    @Test
    void usesFullAiFallbackWhenCatalogCandidatesCannotFillRequestedCount() {
        User user = User.builder().userId("user-id").build();
        LocalDate date = LocalDate.of(2026, 8, 16);
        NutritionGapResult gap = gap(TargetNutrient.IRON);
        IngredientDishRecommendation aiIngredient = new IngredientDishRecommendation(
                ranked(null, "렌틸콩", 1),
                dishes("렌틸콩")
        );
        when(properties.minimumTargetCoverageRatio()).thenReturn(value("0.20"));
        when(nutritionAnalysisService.analyze(
                user, date, 12, List.of("귀리"), value("0.20")))
                .thenReturn(new RecommendationNutritionAnalysis(gap, List.of()));
        when(aiFallbackService.recommend(
                user, gap, 1, java.util.Set.of("귀리")))
                .thenReturn(List.of(aiIngredient));

        RecommendationPlan result = planningService.plan(
                user, date, 1, List.of("귀리")
        );

        assertThat(result.ingredients()).hasSize(1);
        assertThat(result.ingredients().getFirst().rankedIngredient().ingredientName())
                .isEqualTo("렌틸콩");
    }

    @Test
    void returnsNoCandidatesWhenCatalogAndAiCannotFillExactlyTwoIngredients() {
        User user = User.builder().userId("user-id").build();
        LocalDate date = LocalDate.of(2026, 8, 16);
        NutritionGapResult gap = gap(TargetNutrient.IRON);
        when(properties.minimumTargetCoverageRatio()).thenReturn(value("0.20"));
        when(nutritionAnalysisService.analyze(
                user, date, 12, List.of(), value("0.20")))
                .thenReturn(new RecommendationNutritionAnalysis(gap, List.of()));
        when(aiFallbackService.recommend(user, gap, 2, java.util.Set.of()))
                .thenReturn(List.of());

        RecommendationPlan result = planningService.plan(user, date, 2);

        assertThat(result.nutritionGap()).isSameAs(gap);
        assertThat(result.ingredients()).isEmpty();
    }

    private RankedIngredient ranked(String foodId, String name, int rank) {
        return new RankedIngredient(
                foodId,
                name,
                rank,
                TargetNutrient.IRON,
                value("2.4"),
                value("0.30"),
                value("0.50"),
                NutritionValues.builder().ironMg(value("2.4")).build()
        );
    }

    private List<RecommendedDish> dishes(String ingredientName) {
        return List.of(
                new RecommendedDish(null, null, ingredientName + "무침", 1),
                new RecommendedDish(null, null, ingredientName + "국", 2)
        );
    }

    private NutritionGapResult gap(TargetNutrient target) {
        KdrReferenceValues reference = new KdrReferenceValues(
                value("65"), value("30"), value("800"), value("8"),
                value("3500"), value("800"), value("100")
        );
        Map<TargetNutrient, NutrientGap> gaps = new EnumMap<>(TargetNutrient.class);
        for (TargetNutrient nutrient : TargetNutrient.values()) {
            BigDecimal amount = reference.amountOf(nutrient);
            gaps.put(nutrient, new NutrientGap(
                    amount,
                    BigDecimal.ZERO,
                    amount,
                    nutrient == target ? BigDecimal.ONE : BigDecimal.ZERO
            ));
        }
        return new NutritionGapResult(
                reference,
                NutritionValues.builder().build(),
                gaps,
                target
        );
    }

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }
}
