package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.service.FoodNameNormalizer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class IngredientSafetyPolicy {

    private static final Map<String, Set<String>> ALLERGEN_KEYWORDS = createAllergenKeywords();

    public boolean canEvaluate(List<String> allergyCodes) {
        return safeList(allergyCodes).stream()
                .map(this::normalizeCode)
                .allMatch(ALLERGEN_KEYWORDS::containsKey);
    }

    public boolean areAllergensCompatible(List<String> userAllergyCodes,
                                          List<String> foodAllergenCodes) {
        if (!canEvaluate(userAllergyCodes) || !canEvaluate(foodAllergenCodes)) {
            return false;
        }
        Set<String> avoidedKeywords = new HashSet<>();
        safeList(userAllergyCodes).stream()
                .map(this::normalizeCode)
                .map(ALLERGEN_KEYWORDS::get)
                .forEach(avoidedKeywords::addAll);
        return safeList(foodAllergenCodes).stream()
                .map(this::normalizeCode)
                .map(ALLERGEN_KEYWORDS::get)
                .flatMap(Set::stream)
                .noneMatch(avoidedKeywords::contains);
    }

    public boolean isAllowed(Food food, List<String> allergyCodes,
                             List<String> dislikedFoods) {
        if (!canEvaluate(allergyCodes)) {
            return false;
        }
        String ingredient = FoodNameNormalizer.normalizeLookupName(food.getIngredientName());
        if (ingredient.isBlank()) {
            return false;
        }
        boolean disliked = safeList(dislikedFoods).stream()
                .map(FoodNameNormalizer::normalizeLookupName)
                .anyMatch(ingredient::equals);
        if (disliked) {
            return false;
        }

        String searchableName = ingredient
                + "|"
                + FoodNameNormalizer.normalizeLookupName(food.getCanonicalName());
        return safeList(allergyCodes).stream()
                .map(this::normalizeCode)
                .map(ALLERGEN_KEYWORDS::get)
                .flatMap(Set::stream)
                .map(FoodNameNormalizer::normalizeLookupName)
                .noneMatch(searchableName::contains);
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static Map<String, Set<String>> createAllergenKeywords() {
        Map<String, Set<String>> result = new HashMap<>();
        register(result, Set.of("EGG", "EGGS", "난류"), "계란", "달걀", "메추리알", "오리알");
        register(result, Set.of("MILK", "우유"), "우유", "치즈", "버터", "유청", "크림");
        register(result, Set.of("BUCKWHEAT", "메밀"), "메밀");
        register(result, Set.of("PEANUT", "땅콩"), "땅콩");
        register(result, Set.of("SOY", "SOYBEAN", "대두"), "대두", "콩", "두부");
        register(result, Set.of("WHEAT", "밀"), "밀");
        register(result, Set.of("MACKEREL", "고등어"), "고등어");
        register(result, Set.of("CRAB", "게"), "게");
        register(result, Set.of("SHRIMP", "새우"), "새우");
        register(result, Set.of("PORK", "돼지고기"), "돼지고기", "돼지");
        register(result, Set.of("PEACH", "복숭아"), "복숭아");
        register(result, Set.of("TOMATO", "토마토"), "토마토");
        register(result, Set.of("SULFITE", "SULPHITE", "아황산류"), "아황산", "이산화황");
        register(result, Set.of("WALNUT", "호두"), "호두");
        register(result, Set.of("CHICKEN", "닭고기"), "닭고기", "닭");
        register(result, Set.of("BEEF", "쇠고기", "소고기"), "쇠고기", "소고기");
        register(result, Set.of("SQUID", "오징어"), "오징어");
        register(result, Set.of("SHELLFISH", "조개류"), "조개", "굴", "전복", "홍합");
        register(result, Set.of("PINE_NUT", "PINENUT", "잣"), "잣");
        return Map.copyOf(result);
    }

    private static void register(Map<String, Set<String>> target, Set<String> codes,
                                 String... keywords) {
        Set<String> keywordSet = Set.of(keywords);
        codes.forEach(code -> target.put(normalizeStatic(code), keywordSet));
    }

    private static String normalizeStatic(String value) {
        return value.replace("_", "").toUpperCase(Locale.ROOT);
    }
}
