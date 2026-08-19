package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationRes;
import com.centerton.bodybuddy.domain.recommendation.entity.Recommendation;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDish;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationIngredient;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationStatus;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationDishRepository;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationIngredientRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationResponseAssemblerTest {

    @Mock private RecommendationIngredientRepository ingredientRepository;
    @Mock private RecommendationDishRepository dishRepository;
    @InjectMocks private RecommendationResponseAssembler assembler;

    @Test
    void assemblesPersistedNutritionAndCandidateSnapshots() {
        User user = User.builder().userId("user-id").build();
        Meal meal = Meal.createText(user, "식사", LocalDateTime.now());
        Recommendation recommendation = Recommendation.created(
                user,
                meal,
                LocalDate.of(2026, 8, 16),
                TargetNutrient.IRON,
                NutritionValues.builder().proteinG(value("31.5")).build(),
                NutritionValues.builder().ironMg(value("4.2")).build()
        );
        Food ingredientFood = food("ingredient-food", "시금치");
        Food dishFood = food("dish-food", "시금치무침");
        RecommendationIngredient ingredient = RecommendationIngredient.builder()
                .ingredientId("ingredient-id")
                .recommendation(recommendation)
                .food(ingredientFood)
                .rankOrder(1)
                .ingredientName("시금치")
                .reason("철 보완에 도움이 되는 원재료입니다.")
                .nutritionSnapshot(NutritionValues.builder()
                        .proteinG(value("46.8"))
                        .ironMg(value("2.7"))
                        .vitaminCMg(value("80"))
                        .build())
                .build();
        RecommendationDish first = RecommendationDish.builder()
                .recommendationDishId("snapshot-dish-1")
                .ingredient(ingredient)
                .food(dishFood)
                .dishName("시금치무침")
                .rankOrder(1)
                .build();
        RecommendationDish second = RecommendationDish.builder()
                .recommendationDishId("snapshot-dish-2")
                .ingredient(ingredient)
                .dishName("시금치된장국")
                .rankOrder(2)
                .build();
        when(ingredientRepository.findAllByRecommendationRecommendationIdOrderByRankOrderAsc(
                recommendation.getRecommendationId())).thenReturn(List.of(ingredient));
        when(dishRepository.findAllForRecommendation(recommendation.getRecommendationId()))
                .thenReturn(List.of(first, second));

        RecommendationRes result = assembler.assemble(recommendation);

        assertThat(result.getStatus()).isEqualTo(RecommendationStatus.CREATED);
        assertThat(result.getDailyNutrition().getProteinG()).isEqualByComparingTo("31.5");
        assertThat(result.getNutrientGap().getIronMg()).isEqualByComparingTo("4.2");
        assertThat(result.getIngredients()).hasSize(1);
        assertThat(result.getIngredients().get(0).getNutrientCoverages())
                .extracting("nutrient", "coveragePercent")
                .startsWith(
                        org.assertj.core.groups.Tuple.tuple(TargetNutrient.IRON, value("64.3")),
                        org.assertj.core.groups.Tuple.tuple(TargetNutrient.PROTEIN, value("72.0")),
                        org.assertj.core.groups.Tuple.tuple(TargetNutrient.VITAMIN_C, value("0.0"))
                )
                .hasSize(TargetNutrient.values().length);
        assertThat(result.getIngredients().get(0).getDishes())
                .extracting("dishId", "foodId", "dishName", "rank")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "snapshot-dish-1", "dish-food", "시금치무침", 1),
                        org.assertj.core.groups.Tuple.tuple(
                                "snapshot-dish-2", null, "시금치된장국", 2)
                );
    }

    private Food food(String id, String name) {
        return Food.builder()
                .foodId(id)
                .canonicalName(name)
                .normalizedName(name)
                .active(true)
                .build();
    }

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }

}
