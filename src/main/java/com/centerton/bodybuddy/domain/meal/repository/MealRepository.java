package com.centerton.bodybuddy.domain.meal.repository;

import com.centerton.bodybuddy.domain.meal.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MealRepository extends JpaRepository<Meal, String> {
    Optional<Meal> findByMealIdAndUserUserIdAndDeletedAtIsNull(String mealId, String userId);
}
