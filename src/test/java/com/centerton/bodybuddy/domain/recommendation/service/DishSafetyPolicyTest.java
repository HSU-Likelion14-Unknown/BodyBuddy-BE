package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.recommendation.entity.DishTemplate;
import com.centerton.bodybuddy.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DishSafetyPolicyTest {

    private final DishSafetyPolicy policy = new DishSafetyPolicy(
            new IngredientSafetyPolicy()
    );

    @Test
    void rejectsDishContainingUserAllergenOrDislikedIngredient() {
        User user = user(List.of("EGG"), List.of("가지"));

        assertThat(policy.isAllowed(
                user,
                dish("달걀볶음", List.of("달걀"), List.of("EGG"))
        )).isFalse();
        assertThat(policy.isAllowed(
                user,
                dish("채소볶음", List.of("시금치", "가지"), List.of())
        )).isFalse();
        assertThat(policy.isAllowed(
                user,
                dish("시금치나물", List.of("시금치"), List.of())
        )).isTrue();
    }

    @Test
    void failsClosedForIncompleteSafetyMetadata() {
        User user = user(List.of(), List.of());

        assertThat(policy.isAllowed(
                user,
                dish("빈 구성", List.of(), List.of())
        )).isFalse();
        assertThat(policy.isAllowed(
                user,
                dish("알 수 없는 알레르기", List.of("시금치"), List.of("CUSTOM"))
        )).isFalse();
    }

    private User user(List<String> allergies, List<String> dislikes) {
        return User.builder()
                .userId("user-id")
                .allergyCodes(allergies)
                .dislikedFoods(dislikes)
                .build();
    }

    private DishTemplate dish(String name, List<String> ingredients,
                              List<String> allergens) {
        return DishTemplate.builder()
                .dishId(name + "-id")
                .dishName(name)
                .normalizedName(name)
                .ingredientNames(ingredients)
                .allergenCodes(allergens)
                .active(true)
                .build();
    }
}
