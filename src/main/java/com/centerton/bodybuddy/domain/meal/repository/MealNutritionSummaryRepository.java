package com.centerton.bodybuddy.domain.meal.repository;

import com.centerton.bodybuddy.domain.meal.entity.MealNutritionSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealNutritionSummaryRepository extends JpaRepository<MealNutritionSummary, String> {
}
