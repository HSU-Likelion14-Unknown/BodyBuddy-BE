package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.food.entity.Food;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngredientSafetyPolicyTest {

    private final IngredientSafetyPolicy policy = new IngredientSafetyPolicy();

    @Test
    void rejectsKnownAllergenAndDislikedIngredient() {
        assertThat(policy.isAllowed(food("두부"), List.of("SOYBEAN"), List.of())).isFalse();
        assertThat(policy.isAllowed(food("가지"), List.of(), List.of(" 가지 "))).isFalse();
        assertThat(policy.isAllowed(food("시금치"), List.of("MILK"), List.of("가지"))).isTrue();
    }

    @Test
    void acceptsConfiguredEnglishKoreanAndUnderscoredCodes() {
        assertThat(policy.canEvaluate(List.of("EGG", "조개류", "PINE_NUT"))).isTrue();
    }

    @Test
    void cannotEvaluateUnknownAllergyCode() {
        assertThat(policy.canEvaluate(List.of("UNKNOWN_ALLERGEN"))).isFalse();
    }

    @Test
    void doesNotMatchAllergenAcrossIngredientAndCanonicalNameBoundary() {
        assertThat(policy.isAllowed(food("계", "란"), List.of("EGG"), List.of())).isTrue();
    }

    private Food food(String ingredientName) {
        return food(ingredientName, ingredientName);
    }

    private Food food(String ingredientName, String canonicalName) {
        return Food.builder()
                .foodId(ingredientName + "-id")
                .canonicalName(canonicalName)
                .normalizedName(ingredientName)
                .ingredientName(ingredientName)
                .active(true)
                .foodType("INGREDIENT")
                .recommendationCandidate(true)
                .build();
    }
}
