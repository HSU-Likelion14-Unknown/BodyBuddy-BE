package com.centerton.bodybuddy.domain.food.repository;

import com.centerton.bodybuddy.domain.food.entity.FoodNutrition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FoodNutritionRepository extends JpaRepository<FoodNutrition, String> {

    @Query("""
            select nutrition
            from FoodNutrition nutrition
            join fetch nutrition.food
            where nutrition.food.active = true
              and nutrition.food.recommendationCandidate = true
              and nutrition.food.foodType = 'INGREDIENT'
            """)
    List<FoodNutrition> findRecommendationCandidates();

    @Query("""
            select nutrition
            from FoodNutrition nutrition
            join fetch nutrition.food
            where nutrition.food.foodId in :foodIds
              and nutrition.food.active = true
              and nutrition.food.recommendationCandidate = true
              and nutrition.food.foodType = 'INGREDIENT'
            """)
    List<FoodNutrition> findRecommendationCandidatesByFoodIds(
            @Param("foodIds") Collection<String> foodIds
    );
}
