package com.centerton.bodybuddy.domain.food.service;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.entity.FoodAlias;
import com.centerton.bodybuddy.domain.food.repository.FoodAliasRepository;
import com.centerton.bodybuddy.domain.food.repository.FoodNutritionRepository;
import com.centerton.bodybuddy.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FoodMatchingService {

    private final FoodRepository foodRepository;
    private final FoodAliasRepository foodAliasRepository;
    private final FoodNutritionRepository foodNutritionRepository;

    @Transactional(readOnly = true)
    public Optional<FoodCatalogMatch> findById(String foodId) {
        if (foodId == null || foodId.isBlank()) {
            return Optional.empty();
        }
        return foodRepository.findByFoodIdAndActiveTrue(foodId.trim())
                .map(this::withNutrition);
    }

    @Transactional(readOnly = true)
    public Optional<FoodCatalogMatch> matchByName(String foodName) {
        String normalizedName = FoodNameNormalizer.normalizeCatalogName(foodName);
        if (normalizedName.isBlank()) {
            return Optional.empty();
        }

        Optional<Food> exactMatch = foodRepository
                .findFirstByNormalizedNameAndActiveTrueOrderByRecommendationCandidateDescFoodIdAsc(
                        normalizedName
                );
        if (exactMatch.isPresent()) {
            return exactMatch.map(this::withNutrition);
        }

        String lookupName = FoodNameNormalizer.normalizeLookupName(foodName);
        if (lookupName.isBlank()) {
            return Optional.empty();
        }
        return foodAliasRepository.findByNormalizedAliasAndFoodActiveTrue(lookupName)
                .map(FoodAlias::getFood)
                .map(this::withNutrition);
    }

    private FoodCatalogMatch withNutrition(Food food) {
        return new FoodCatalogMatch(
                food,
                foodNutritionRepository.findById(food.getFoodId()).orElse(null)
        );
    }
}
