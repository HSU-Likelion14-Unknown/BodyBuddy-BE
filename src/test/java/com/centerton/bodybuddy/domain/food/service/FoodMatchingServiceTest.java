package com.centerton.bodybuddy.domain.food.service;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.entity.FoodAlias;
import com.centerton.bodybuddy.domain.food.entity.FoodNutrition;
import com.centerton.bodybuddy.domain.food.repository.FoodAliasRepository;
import com.centerton.bodybuddy.domain.food.repository.FoodNutritionRepository;
import com.centerton.bodybuddy.domain.food.repository.FoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodMatchingServiceTest {

    @Mock private FoodRepository foodRepository;
    @Mock private FoodAliasRepository foodAliasRepository;
    @Mock private FoodNutritionRepository foodNutritionRepository;

    private FoodMatchingService foodMatchingService;

    @BeforeEach
    void setUp() {
        foodMatchingService = new FoodMatchingService(
                foodRepository,
                foodAliasRepository,
                foodNutritionRepository
        );
    }

    @Test
    void prefersExactCatalogNameMatch() {
        Food food = food("food-id", "두부,부침용");
        FoodNutrition nutrition = FoodNutrition.builder().food(food).build();
        when(foodRepository
                .findFirstByNormalizedNameAndActiveTrueOrderByRecommendationCandidateDescFoodIdAsc(
                        "두부,부침용"
                ))
                .thenReturn(Optional.of(food));
        when(foodNutritionRepository.findById("food-id")).thenReturn(Optional.of(nutrition));

        Optional<FoodCatalogMatch> result = foodMatchingService.matchByName(" 두부 , 부침용 ");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().food().getFoodId()).isEqualTo("food-id");
        assertThat(result.orElseThrow().nutrition()).isSameAs(nutrition);
        verify(foodAliasRepository, never()).findByNormalizedAliasAndFoodActiveTrue("두부부침용");
    }

    @Test
    void fallsBackToIngredientAlias() {
        Food food = food("food-id", "망둑어, 풀망둑, 생것");
        FoodAlias alias = FoodAlias.builder().food(food).build();
        when(foodRepository
                .findFirstByNormalizedNameAndActiveTrueOrderByRecommendationCandidateDescFoodIdAsc(
                        "망둑어 (풀망둑)"
                ))
                .thenReturn(Optional.empty());
        when(foodAliasRepository.findByNormalizedAliasAndFoodActiveTrue("망둑어풀망둑"))
                .thenReturn(Optional.of(alias));
        when(foodNutritionRepository.findById("food-id")).thenReturn(Optional.empty());

        Optional<FoodCatalogMatch> result = foodMatchingService.matchByName("망둑어 (풀망둑)");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().food().getFoodId()).isEqualTo("food-id");
        assertThat(result.orElseThrow().nutrition()).isNull();
    }

    @Test
    void returnsEmptyForUnknownFoodName() {
        when(foodRepository
                .findFirstByNormalizedNameAndActiveTrueOrderByRecommendationCandidateDescFoodIdAsc(
                        "없는 음식"
                ))
                .thenReturn(Optional.empty());
        when(foodAliasRepository.findByNormalizedAliasAndFoodActiveTrue("없는음식"))
                .thenReturn(Optional.empty());

        assertThat(foodMatchingService.matchByName("없는 음식")).isEmpty();
    }

    private Food food(String foodId, String normalizedName) {
        return Food.builder()
                .foodId(foodId)
                .canonicalName(normalizedName)
                .normalizedName(normalizedName)
                .active(true)
                .build();
    }
}
