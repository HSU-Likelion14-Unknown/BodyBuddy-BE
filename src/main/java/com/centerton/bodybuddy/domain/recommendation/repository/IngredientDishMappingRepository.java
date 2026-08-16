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
            join fetch mapping.ingredientFood ingredient
            join fetch mapping.dish dish
            left join fetch dish.food
            where ingredient.foodId in :ingredientFoodIds
              and mapping.active = true
              and dish.active = true
            order by mapping.priority asc, dish.normalizedName asc, dish.dishId asc
            """)
    List<IngredientDishMapping> findActiveMappings(
            @Param("ingredientFoodIds") Collection<String> ingredientFoodIds
    );
}
