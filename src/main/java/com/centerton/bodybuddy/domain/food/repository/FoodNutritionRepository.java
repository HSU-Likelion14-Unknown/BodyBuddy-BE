package com.centerton.bodybuddy.domain.food.repository;

import com.centerton.bodybuddy.domain.food.entity.FoodNutrition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodNutritionRepository extends JpaRepository<FoodNutrition, String> {
}
