package com.centerton.bodybuddy.domain.meal.repository;

import com.centerton.bodybuddy.domain.meal.entity.Meal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MealRepository extends JpaRepository<Meal, String> {
    Optional<Meal> findByMealIdAndUserUserId(String mealId, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select meal
            from Meal meal
            where meal.mealId = :mealId
              and meal.user.userId = :userId
            """)
    Optional<Meal> findOwnedByIdForUpdate(
            @Param("mealId") String mealId,
            @Param("userId") String userId
    );
}
