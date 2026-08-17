package com.centerton.bodybuddy.domain.recommendation.repository;

import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
