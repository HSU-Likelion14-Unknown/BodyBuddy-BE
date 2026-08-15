package com.centerton.bodybuddy.domain.food.service;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.entity.FoodNutrition;

public record FoodCatalogMatch(
        Food food,
        FoodNutrition nutrition
) {
}
