package com.centerton.bodybuddy.domain.meal.repository;

import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select meal
        from Meal meal
        join fetch meal.user
        where meal.mealId = :mealId
        """)
    Optional<Meal> findByIdForReactionUpdate(
            @Param("mealId") String mealId
    );

    @Query("""
        select meal
        from Meal meal
        join fetch meal.user
        where meal.mealId = :mealId
        """)
    Optional<Meal> findByIdWithUser(
            @Param("mealId") String mealId
    );

    @Query("""
            select meal
            from Meal meal
            join fetch meal.user user
            where user.userId in :userIds
              and user.shareToRoom = true
              and meal.eatenAt >= :startAt
              and meal.eatenAt < :endAt
              and meal.status in :statuses
            order by meal.eatenAt desc, meal.mealId asc
            """)
    List<Meal> findSharedRoomFeed(
            @Param("userIds") Collection<String> userIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("statuses") Collection<MealStatus> statuses
    );
}
