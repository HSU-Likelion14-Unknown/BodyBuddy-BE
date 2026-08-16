package com.centerton.bodybuddy.domain.meal.repository;

import com.centerton.bodybuddy.domain.meal.entity.MealNutritionSummary;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MealNutritionSummaryRepository extends JpaRepository<MealNutritionSummary, String> {

    @Query("""
            select summary
            from MealNutritionSummary summary
            where summary.meal.user.userId = :userId
              and summary.meal.status in :statuses
              and summary.meal.eatenAt >= :startUtc
              and summary.meal.eatenAt < :endUtc
            """)
    List<MealNutritionSummary> findDailySummaries(
            @Param("userId") String userId,
            @Param("statuses") Collection<MealStatus> statuses,
            @Param("startUtc") LocalDateTime startUtc,
            @Param("endUtc") LocalDateTime endUtc
    );
}
