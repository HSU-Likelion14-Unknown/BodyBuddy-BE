package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.food.service.FoodNameNormalizer;
import com.centerton.bodybuddy.domain.recommendation.entity.DishTemplate;
import com.centerton.bodybuddy.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DishSafetyPolicy {

    private final IngredientSafetyPolicy ingredientSafetyPolicy;

    public boolean isAllowed(User user, DishTemplate dish) {
        if (user == null || dish == null || !dish.isActive()) {
            return false;
        }
        List<String> ingredientNames = safeList(dish.getIngredientNames());
        List<String> normalizedIngredients = ingredientNames.stream()
                .map(FoodNameNormalizer::normalizeLookupName)
                .toList();
        if (dish.getAllergenCodes() == null
                || ingredientNames.isEmpty()
                || ingredientNames.stream().anyMatch(this::isBlank)
                || normalizedIngredients.stream().anyMatch(String::isBlank)) {
            return false;
        }
        if (!ingredientSafetyPolicy.areAllergensCompatible(
                user.getAllergyCodes(),
                dish.getAllergenCodes()
        )) {
            return false;
        }

        Set<String> disliked = new HashSet<>();
        safeList(user.getDislikedFoods()).stream()
                .map(FoodNameNormalizer::normalizeLookupName)
                .filter(value -> !value.isBlank())
                .forEach(disliked::add);
        return normalizedIngredients.stream()
                .noneMatch(disliked::contains);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
