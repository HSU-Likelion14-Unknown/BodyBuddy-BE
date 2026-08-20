package com.centerton.bodybuddy.domain.meal.repository;

import com.centerton.bodybuddy.domain.meal.entity.MealItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MealItemRepository extends JpaRepository<MealItem, String> {
    List<MealItem> findAllByMealMealIdOrderBySortOrderAsc(String mealId);

    void deleteAllByMealMealId(String mealId);

    @Query("""
            select item
            from MealItem item
            join fetch item.meal meal
            where meal.mealId in :mealIds
            order by meal.eatenAt, meal.mealId, item.sortOrder
            """)
    List<MealItem> findAllByMealIds(
            @Param("mealIds") Collection<String> mealIds
    );

    @Query("""
            select
                item.meal.mealId,
                item.foodName
            from MealItem item
            where item.meal.mealId in :mealIds
            order by item.meal.mealId, item.sortOrder
            """)
    List<Object[]> findFoodNamesByMealIds(
            @Param("mealIds") Collection<String> mealIds
    );
}
