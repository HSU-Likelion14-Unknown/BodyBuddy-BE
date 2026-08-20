package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.client.AiDishCandidate;
import com.centerton.bodybuddy.domain.recommendation.client.AiDishRecommendationInput;
import com.centerton.bodybuddy.domain.recommendation.client.AiIngredientCandidate;
import com.centerton.bodybuddy.domain.recommendation.client.AiIngredientRecommendationClient;
import com.centerton.bodybuddy.domain.recommendation.client.AiIngredientRecommendationInput;
import com.centerton.bodybuddy.domain.recommendation.config.RecommendationPolicyProperties;
import com.centerton.bodybuddy.domain.recommendation.model.IngredientDishRecommendation;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutrientGap;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendedDish;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import com.centerton.bodybuddy.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiIngredientFallbackServiceTest {

    @Mock
    private AiIngredientRecommendationClient client;

    private AiIngredientFallbackService service;

    @BeforeEach
    void setUp() {
        RecommendationPolicyProperties properties = new RecommendationPolicyProperties();
        properties.setMinimumTargetCoveragePercent(value("20"));
        IngredientSafetyPolicy ingredientSafetyPolicy = new IngredientSafetyPolicy();
        service = new AiIngredientFallbackService(
                client,
                ingredientSafetyPolicy,
                new DishSafetyPolicy(ingredientSafetyPolicy),
                properties
        );
    }

    @Test
    void acceptsOnlyNewSafeCandidateMeetingMinimumCoverage() {
        when(client.recommend(any(AiIngredientRecommendationInput.class))).thenReturn(List.of(
                candidate("시금치", "4.0"),
                candidate("미나리", "1.59"),
                candidate("렌틸콩", "1.60")
        ));
        User user = User.builder()
                .userId("user-id")
                .allergyCodes(List.of())
                .dislikedFoods(List.of())
                .build();

        List<IngredientDishRecommendation> result = service.recommend(
                user,
                ironGap(),
                1,
                List.of("시금치")
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().rankedIngredient().ingredientName())
                .isEqualTo("렌틸콩");
        assertThat(result.getFirst().rankedIngredient().dailyTargetCoverageRatio())
                .isEqualByComparingTo("0.20");
        assertThat(result.getFirst().dishes()).hasSize(2);
    }

    @Test
    void completesDishesForKnownCatalogIngredientAndRejectsUnrelatedDish() {
        when(client.recommendDishes(any(AiDishRecommendationInput.class)))
                .thenReturn(List.of(
                        new AiDishCandidate(
                                "시금치나물", List.of("시금치"), List.of()
                        ),
                        new AiDishCandidate(
                                "시금치국", List.of("시금치", "된장"), List.of("SOY")
                        ),
                        new AiDishCandidate(
                                "감자전", List.of("감자"), List.of()
                        )
                ));
        User user = User.builder()
                .userId("user-id")
                .allergyCodes(List.of())
                .dislikedFoods(List.of())
                .build();
        RankedIngredient ingredient = new RankedIngredient(
                "spinach-food",
                "시금치",
                1,
                TargetNutrient.IRON,
                value("2.4"),
                value("0.3"),
                value("0.5"),
                NutritionValues.builder().ironMg(value("2.4")).build()
        );

        List<RecommendedDish> result = service.completeDishes(user, ingredient);

        assertThat(result)
                .extracting(RecommendedDish::dishName)
                .containsExactly("시금치나물", "시금치국");
        ArgumentCaptor<AiDishRecommendationInput> inputCaptor =
                ArgumentCaptor.forClass(AiDishRecommendationInput.class);
        verify(client).recommendDishes(inputCaptor.capture());
        assertThat(inputCaptor.getValue().ingredientName()).isEqualTo("시금치");
    }

    @Test
    void requiresExactNormalizedIngredientNameWhenCompletingDishes() {
        when(client.recommendDishes(any(AiDishRecommendationInput.class)))
                .thenReturn(List.of(
                        new AiDishCandidate("콩나물국", List.of("콩나물"), List.of()),
                        new AiDishCandidate("렌틸콩샐러드", List.of("렌틸콩"), List.of()),
                        new AiDishCandidate("콩조림", List.of("콩"), List.of())
                ));
        User user = User.builder()
                .userId("user-id")
                .allergyCodes(List.of())
                .dislikedFoods(List.of())
                .build();
        RankedIngredient ingredient = new RankedIngredient(
                "soy-food",
                "콩",
                1,
                TargetNutrient.IRON,
                value("2.4"),
                value("0.3"),
                value("0.5"),
                NutritionValues.builder().ironMg(value("2.4")).build()
        );

        List<RecommendedDish> result = service.completeDishes(user, ingredient);

        assertThat(result)
                .extracting(RecommendedDish::dishName)
                .containsExactly("콩조림");
    }

    private AiIngredientCandidate candidate(String name, String ironMg) {
        return new AiIngredientCandidate(
                name,
                List.of(),
                NutritionValues.builder().ironMg(value(ironMg)).build(),
                List.of(
                        new AiDishCandidate(name + " 샐러드", List.of(name), List.of()),
                        new AiDishCandidate(name + " 수프", List.of(name), List.of())
                )
        );
    }

    private NutritionGapResult ironGap() {
        KdrReferenceValues reference = new KdrReferenceValues(
                value("65"), value("30"), value("800"), value("8"),
                value("3500"), value("800"), value("100")
        );
        Map<TargetNutrient, NutrientGap> gaps = new EnumMap<>(TargetNutrient.class);
        for (TargetNutrient nutrient : TargetNutrient.values()) {
            gaps.put(nutrient, new NutrientGap(
                    reference.amountOf(nutrient),
                    BigDecimal.ZERO,
                    reference.amountOf(nutrient),
                    nutrient == TargetNutrient.IRON ? BigDecimal.ONE : BigDecimal.ZERO
            ));
        }
        return new NutritionGapResult(
                reference,
                NutritionValues.builder().build(),
                gaps,
                TargetNutrient.IRON
        );
    }

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }
}
