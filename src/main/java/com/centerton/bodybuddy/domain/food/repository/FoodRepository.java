package com.centerton.bodybuddy.domain.food.repository;

import com.centerton.bodybuddy.domain.food.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, String> {

    Optional<Food> findByFoodIdAndActiveTrue(String foodId);

    Optional<Food> findFirstByNormalizedNameAndActiveTrueOrderByRecommendationCandidateDescFoodIdAsc(
            String normalizedName
    );
}
