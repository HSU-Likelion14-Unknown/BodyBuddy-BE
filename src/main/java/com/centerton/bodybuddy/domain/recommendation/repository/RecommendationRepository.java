package com.centerton.bodybuddy.domain.recommendation.repository;

import com.centerton.bodybuddy.domain.recommendation.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, String> {

    Optional<Recommendation> findByMealMealId(String mealId);

    Optional<Recommendation> findByRecommendationIdAndUserUserId(
            String recommendationId,
            String userId
    );
}
