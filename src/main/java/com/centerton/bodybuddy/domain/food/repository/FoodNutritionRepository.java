package com.centerton.bodybuddy.domain.food.repository;

import com.centerton.bodybuddy.domain.food.entity.FoodNutrition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FoodNutritionRepository extends JpaRepository<FoodNutrition, String> {

    @Query("""
            select nutrition
            from FoodNutrition nutrition
            join fetch nutrition.food food
            where food.active = true
              and food.recommendationCandidate = true
              and food.foodType = 'INGREDIENT'
            """)
    List<FoodNutrition> findRecommendationCandidates();
}
