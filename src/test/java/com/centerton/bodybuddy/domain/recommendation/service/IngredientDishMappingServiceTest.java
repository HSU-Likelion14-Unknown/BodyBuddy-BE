package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.recommendation.entity.DishTemplate;
import com.centerton.bodybuddy.domain.recommendation.entity.IngredientDishMapping;
import com.centerton.bodybuddy.domain.recommendation.model.IngredientDishRecommendation;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import com.centerton.bodybuddy.domain.recommendation.repository.IngredientDishMappingRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngredientDishMappingServiceTest {

    @Mock
    private IngredientDishMappingRepository mappingRepository;

    private IngredientDishMappingService mappingService;

    @BeforeEach
    void setUp() {
        mappingService = new IngredientDishMappingService(
                mappingRepository,
                new DishSafetyPolicy(new IngredientSafetyPolicy())
        );
    }

    @Test
    void skipsIngredientWithFewerThanTwoSafeDishesAndUsesNextCandidate() {
        Food spinach = ingredient("spinach", "시금치");
        Food broccoli = ingredient("broccoli", "브로콜리");
        User user = user(List.of("EGG"), List.of("가지"));
        List<RankedIngredient> ranked = List.of(
                ranked(spinach, 1),
                ranked(broccoli, 2)
        );
        when(mappingRepository.findActiveMappings(List.of("spinach", "broccoli")))
                .thenReturn(List.of(
                        mapping(spinach, dish("spinach-egg", "시금치달걀볶음",
                                List.of("시금치", "달걀"), List.of("EGG")), 1),
                        mapping(spinach, dish("spinach-side", "시금치나물",
                                List.of("시금치"), List.of()), 2),
                        mapping(broccoli, dish("broccoli-steamed", "데친 브로콜리",
                                List.of("브로콜리"), List.of()), 1),
                        mapping(broccoli, dish("broccoli-egg", "브로콜리 달걀볶음",
                                List.of("브로콜리", "달걀"), List.of("EGG")), 2),
                        mapping(broccoli, dish("broccoli-stir", "브로콜리볶음",
                                List.of("브로콜리"), List.of()), 3),
                        mapping(broccoli, dish("broccoli-salad", "브로콜리샐러드",
                                List.of("브로콜리", "가지"), List.of()), 4)
                ));

        List<IngredientDishRecommendation> result = mappingService.map(user, ranked, 1);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().rankedIngredient().ingredientName())
                .isEqualTo("브로콜리");
        assertThat(result.getFirst().dishes())
                .extracting(dish -> dish.dishName())
                .containsExactly("데친 브로콜리", "브로콜리볶음");
        assertThat(result.getFirst().dishes())
                .extracting(dish -> dish.rank())
                .containsExactly(1, 2);
    }

    @Test
    void deduplicatesDishNamesAndReturnsAtMostThree() {
        Food spinach = ingredient("spinach", "시금치");
        List<RankedIngredient> ranked = List.of(ranked(spinach, 1));
        when(mappingRepository.findActiveMappings(List.of("spinach"))).thenReturn(List.of(
                mapping(spinach, dish("first", "시금치나물",
                        List.of("시금치"), List.of()), 1),
                mapping(spinach, dish("duplicate", " 시금치 나물 ",
                        List.of("시금치"), List.of()), 2),
                mapping(spinach, dish("second", "시금치국",
                        List.of("시금치"), List.of()), 3),
                mapping(spinach, dish("third", "시금치볶음",
                        List.of("시금치"), List.of()), 4),
                mapping(spinach, dish("fourth", "시금치샐러드",
                        List.of("시금치"), List.of()), 5)
        ));

        List<IngredientDishRecommendation> result = mappingService.map(
                user(List.of(), List.of()),
                ranked,
                1
        );

        assertThat(result.getFirst().dishes())
                .extracting(dish -> dish.dishName())
                .containsExactly("시금치나물", "시금치국", "시금치볶음");
    }

    @Test
    void returnsEmptyWithoutCandidates() {
        assertThat(mappingService.map(user(List.of(), List.of()), List.of(), 3)).isEmpty();
        verify(mappingRepository, never()).findActiveMappings(List.of());
    }

    @Test
    void capsRecommendedIngredientsAtThree() {
        List<Food> ingredients = IntStream.rangeClosed(1, 4)
                .mapToObj(number -> ingredient("food-" + number, "원재료" + number))
                .toList();
        List<RankedIngredient> ranked = IntStream.range(0, ingredients.size())
                .mapToObj(index -> ranked(ingredients.get(index), index + 1))
                .toList();
        List<IngredientDishMapping> mappings = ingredients.stream()
                .flatMap(ingredient -> List.of(
                        mapping(ingredient, dish(
                                ingredient.getFoodId() + "-dish-1",
                                ingredient.getIngredientName() + "요리1",
                                List.of(ingredient.getIngredientName()),
                                List.of()
                        ), 1),
                        mapping(ingredient, dish(
                                ingredient.getFoodId() + "-dish-2",
                                ingredient.getIngredientName() + "요리2",
                                List.of(ingredient.getIngredientName()),
                                List.of()
                        ), 2)
                ).stream())
                .toList();
        when(mappingRepository.findActiveMappings(
                ingredients.stream().map(Food::getFoodId).toList()
        )).thenReturn(mappings);

        List<IngredientDishRecommendation> result = mappingService.map(
                user(List.of(), List.of()),
                ranked,
                10
        );

        assertThat(result).hasSize(3);
    }

    private User user(List<String> allergies, List<String> dislikes) {
        return User.builder()
                .userId("user-id")
                .allergyCodes(allergies)
                .dislikedFoods(dislikes)
                .build();
    }

    private Food ingredient(String foodId, String name) {
        return Food.builder()
                .foodId(foodId)
                .canonicalName(name)
                .normalizedName(name)
                .ingredientName(name)
                .foodType("INGREDIENT")
                .active(true)
                .recommendationCandidate(true)
                .build();
    }

    private RankedIngredient ranked(Food food, int rank) {
        return new RankedIngredient(
                food.getFoodId(),
                food.getIngredientName(),
                rank,
                TargetNutrient.IRON,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                null
        );
    }

    private DishTemplate dish(String dishId, String name, List<String> ingredients,
                              List<String> allergens) {
        return DishTemplate.builder()
                .dishId(dishId)
                .dishName(name)
                .normalizedName(name)
                .ingredientNames(ingredients)
                .allergenCodes(allergens)
                .active(true)
                .build();
    }

    private IngredientDishMapping mapping(Food ingredient, DishTemplate dish,
                                          int priority) {
        return IngredientDishMapping.builder()
                .mappingId(ingredient.getFoodId() + "-" + dish.getDishId())
                .ingredientFood(ingredient)
                .dish(dish)
                .priority(priority)
                .active(true)
                .build();
    }
}
