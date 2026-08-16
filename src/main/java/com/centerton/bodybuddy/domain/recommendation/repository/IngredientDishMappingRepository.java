package com.centerton.bodybuddy.domain.recommendation.repository;

import com.centerton.bodybuddy.domain.recommendation.entity.IngredientDishMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IngredientDishMappingRepository
        extends JpaRepository<IngredientDishMapping, String> {

    @Query("""
            select mapping
            from IngredientDishMapping mapping
            join fetch mapping.ingredientFood
            join fetch mapping.dish
            left join fetch mapping.dish.food
            where mapping.ingredientFood.foodId in :ingredientFoodIds
              and mapping.active = true
              and mapping.dish.active = true
            order by mapping.priority asc,
                     mapping.dish.normalizedName asc,
                     mapping.dish.dishId asc
            """)
    List<IngredientDishMapping> findActiveMappings(
            @Param("ingredientFoodIds") Collection<String> ingredientFoodIds
    );

    @Query("""
            select mapping.ingredientFood.foodId
            from IngredientDishMapping mapping
            where mapping.active = true
              and mapping.dish.active = true
              and mapping.ingredientFood.active = true
              and mapping.ingredientFood.recommendationCandidate = true
              and mapping.ingredientFood.foodType = 'INGREDIENT'
            group by mapping.ingredientFood.foodId
            having count(distinct mapping.dish.dishId) >= 2
            order by mapping.ingredientFood.foodId asc
            """)
    List<String> findMappableIngredientFoodIds();
}
