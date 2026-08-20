package com.centerton.bodybuddy.domain.recommendation.repository;

import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @Query("""
            select dish
            from RecommendationDish dish
            join fetch dish.ingredient ingredient
            join fetch ingredient.recommendation recommendation
            left join fetch dish.food
            where recommendation.recommendationId in :recommendationIds
            order by recommendation.createdAt,
                     recommendation.recommendationId,
                     ingredient.rankOrder,
                     dish.rankOrder
            """)
    List<RecommendationDish> findAllForRecommendations(
            @Param("recommendationIds") Collection<String> recommendationIds
    );

    @Modifying
    @Query("""
            delete from RecommendationDish dish
            where dish.ingredient.recommendation.recommendationId = :recommendationId
            """)
    int deleteAllForRecommendation(@Param("recommendationId") String recommendationId);
}
