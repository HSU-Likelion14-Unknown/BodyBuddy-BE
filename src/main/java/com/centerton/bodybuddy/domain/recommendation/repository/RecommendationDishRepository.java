package com.centerton.bodybuddy.domain.recommendation.repository;

import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecommendationDishRepository
        extends JpaRepository<RecommendationDish, String> {

    @Query("""
            select dish
            from RecommendationDish dish
            join fetch dish.ingredient
            left join fetch dish.food
            where dish.ingredient.recommendation.recommendationId = :recommendationId
            order by dish.ingredient.rankOrder asc, dish.rankOrder asc
            """)
    List<RecommendationDish> findAllForRecommendation(
            @Param("recommendationId") String recommendationId
    );
}
