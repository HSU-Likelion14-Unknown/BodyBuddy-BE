package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.entity.FoodNutrition;
import com.centerton.bodybuddy.domain.food.repository.FoodNutritionRepository;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutrientGap;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import com.centerton.bodybuddy.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngredientRankingServiceTest {

    @Mock
    private FoodNutritionRepository foodNutritionRepository;

    private IngredientRankingService rankingService;
    private NutrientGapCalculator gapCalculator;

    @BeforeEach
    void setUp() {
        rankingService = new IngredientRankingService(
                foodNutritionRepository,
                new IngredientSafetyPolicy()
        );
        gapCalculator = new NutrientGapCalculator();
    }

    @Test
    void ranksByTargetNutrientAndExcludesAllergyAndDislike() {
        NutritionGapResult ironGap = ironGap();
        User user = user(List.of("PORK"), List.of("간"));
        when(foodNutritionRepository.findRecommendationCandidates()).thenReturn(List.of(
                nutrition("pork", "돼지고기", "20", "100"),
                nutrition("liver", "간", "15", "100"),
                nutrition("spinach", "시금치", "4", "100"),
                nutrition("sesame", "참깨", "8", "200")
        ));

        List<RankedIngredient> result = rankingService.rank(user, ironGap, 3);

        assertThat(result).extracting(RankedIngredient::ingredientName)
                .containsExactly("시금치", "참깨");
        assertThat(result).extracting(RankedIngredient::rank).containsExactly(1, 2);
        assertThat(result.getFirst().targetNutrient()).isEqualTo(TargetNutrient.IRON);
        assertThat(result.getFirst().targetAmountPer100g()).isEqualByComparingTo("4.00");
    }

    @Test
    void deduplicatesIngredientAndUsesFoodIdAsStableTieBreaker() {
        NutritionGapResult ironGap = ironGap();
        User user = user(List.of(), List.of());
        when(foodNutritionRepository.findRecommendationCandidates()).thenReturn(List.of(
                nutrition("b-food", "시금치", "5", "100"),
                nutrition("a-food", "시금치", "5", "100"),
                nutrition("c-food", "미나리", "5", "100")
        ));

        List<RankedIngredient> result = rankingService.rank(user, ironGap, 3);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().foodId()).isEqualTo("c-food");
        assertThat(result.get(1).foodId()).isEqualTo("a-food");
    }

    @Test
    void failsClosedWhenAllergyCodeIsUnknown() {
        List<RankedIngredient> result = rankingService.rank(
                user(List.of("CUSTOM_UNKNOWN"), List.of()),
                ironGap(),
                3
        );

        assertThat(result).isEmpty();
        verify(foodNutritionRepository, never()).findRecommendationCandidates();
    }

    @Test
    void returnsNoIngredientsWhenDailyNutritionIsBalanced() {
        KdrReferenceValues reference = reference();
        NutritionValues sufficient = NutritionValues.builder()
                .proteinG(reference.proteinG())
                .fiberG(reference.fiberG())
                .calciumMg(reference.calciumMg())
                .ironMg(reference.ironMg())
                .potassiumMg(reference.potassiumMg())
                .vitaminAMcgRae(reference.vitaminAMcgRae())
                .vitaminCMg(reference.vitaminCMg())
                .build();

        List<RankedIngredient> result = rankingService.rank(
                user(List.of(), List.of()),
                gapCalculator.calculate(reference, sufficient),
                3
        );

        assertThat(result).isEmpty();
        verify(foodNutritionRepository, never()).findRecommendationCandidates();
    }

    @Test
    void skipsNutrientsMissingFromPartialGapMap() {
        NutrientGap iron = new NutrientGap(
                new BigDecimal("8"),
                BigDecimal.ZERO,
                new BigDecimal("8"),
                BigDecimal.ONE
        );
        NutritionGapResult partialGap = new NutritionGapResult(
                reference(),
                NutritionValues.builder().build(),
                Map.of(TargetNutrient.IRON, iron),
                TargetNutrient.IRON
        );
        User user = user(List.of(), List.of());
        when(foodNutritionRepository.findRecommendationCandidates()).thenReturn(List.of(
                nutrition("spinach", "시금치", "4", "100")
        ));

        List<RankedIngredient> result = rankingService.rank(user, partialGap, 1);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().ingredientName()).isEqualTo("시금치");
    }

    @Test
    void loadsOnlyMappableCandidatesInBoundedBatches() {
        List<String> foodIds = IntStream.rangeClosed(1, 101)
                .mapToObj(number -> "food-" + number)
                .toList();
        List<String> firstBatch = foodIds.subList(0, 100);
        List<String> secondBatch = foodIds.subList(100, 101);
        when(foodNutritionRepository.findRecommendationCandidatesByFoodIds(firstBatch))
                .thenReturn(List.of());
        when(foodNutritionRepository.findRecommendationCandidatesByFoodIds(secondBatch))
                .thenReturn(List.of());

        List<RankedIngredient> result = rankingService.rankMappable(
                user(List.of(), List.of()),
                ironGap(),
                foodIds
        );

        assertThat(result).isEmpty();
        verify(foodNutritionRepository).findRecommendationCandidatesByFoodIds(firstBatch);
        verify(foodNutritionRepository).findRecommendationCandidatesByFoodIds(secondBatch);
        verify(foodNutritionRepository, never()).findRecommendationCandidates();
    }

    @Test
    void excludesPreviousNamesAndCandidatesBelowMinimumTargetCoverage() {
        User user = user(List.of(), List.of());
        List<String> foodIds = List.of("weak", "previous", "valid");
        when(foodNutritionRepository.findRecommendationCandidatesByFoodIds(foodIds))
                .thenReturn(List.of(
                        nutrition("weak", "미나리", "1.59", "100"),
                        nutrition("previous", "시금치", "4", "100"),
                        nutrition("valid", "렌틸콩", "1.60", "100")
                ));

        List<RankedIngredient> result = rankingService.rankMappable(
                user,
                ironGap(),
                foodIds,
                List.of(" 시금치 "),
                new BigDecimal("0.20")
        );

        assertThat(result).extracting(RankedIngredient::ingredientName)
                .containsExactly("렌틸콩");
        assertThat(result.getFirst().dailyTargetCoverageRatio())
                .isEqualByComparingTo("0.20");
    }

    private NutritionGapResult ironGap() {
        KdrReferenceValues reference = reference();
        NutritionValues daily = NutritionValues.builder()
                .proteinG(reference.proteinG())
                .fiberG(reference.fiberG())
                .calciumMg(reference.calciumMg())
                .ironMg(BigDecimal.ZERO)
                .potassiumMg(reference.potassiumMg())
                .vitaminAMcgRae(reference.vitaminAMcgRae())
                .vitaminCMg(reference.vitaminCMg())
                .build();
        return gapCalculator.calculate(reference, daily);
    }

    private KdrReferenceValues reference() {
        return new KdrReferenceValues(
                new BigDecimal("65"),
                new BigDecimal("30"),
                new BigDecimal("800"),
                new BigDecimal("8"),
                new BigDecimal("3500"),
                new BigDecimal("800"),
                new BigDecimal("100")
        );
    }

    private User user(List<String> allergies, List<String> disliked) {
        return User.builder()
                .userId("user-id")
                .allergyCodes(allergies)
                .dislikedFoods(disliked)
                .build();
    }

    private FoodNutrition nutrition(String foodId, String ingredientName,
                                    String ironMg, String referenceAmount) {
        Food food = Food.builder()
                .foodId(foodId)
                .canonicalName(ingredientName)
                .normalizedName(ingredientName)
                .ingredientName(ingredientName)
                .active(true)
                .foodType("INGREDIENT")
                .recommendationCandidate(true)
                .build();
        return FoodNutrition.builder()
                .food(food)
                .referenceAmount(new BigDecimal(referenceAmount))
                .referenceUnit("g")
                .nutrition(NutritionValues.builder()
                        .proteinG(new BigDecimal("10"))
                        .fiberG(new BigDecimal("2"))
                        .calciumMg(new BigDecimal("100"))
                        .ironMg(new BigDecimal(ironMg))
                        .potassiumMg(new BigDecimal("300"))
                        .vitaminAMcgRae(new BigDecimal("20"))
                        .vitaminCMg(new BigDecimal("5"))
                        .build())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
