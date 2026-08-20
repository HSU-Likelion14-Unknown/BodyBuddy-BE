package com.centerton.bodybuddy.domain.recommendation.repository;

import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecommendationIngredientRepository
        extends JpaRepository<RecommendationIngredient, String> {

    List<RecommendationIngredient> findAllByRecommendationRecommendationIdOrderByRankOrderAsc(
            String recommendationId
    );

    Optional<RecommendationIngredient>
    findByIngredientIdAndRecommendationRecommendationId(
            String ingredientId,
            String recommendationId
    );

    @Query("""
            select ingredient
            from RecommendationIngredient ingredient
            join fetch ingredient.recommendation recommendation
            left join fetch ingredient.food
            where recommendation.recommendationId in :recommendationIds
            order by recommendation.createdAt,
                     recommendation.recommendationId,
                     ingredient.rankOrder
            """)
    List<RecommendationIngredient> findAllForRecommendations(
            @Param("recommendationIds") Collection<String> recommendationIds
    );

    @Modifying
    @Query("""
            delete from RecommendationIngredient ingredient
            where ingredient.recommendation.recommendationId = :recommendationId
            """)
    int deleteAllForRecommendation(@Param("recommendationId") String recommendationId);
}
