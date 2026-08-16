package com.centerton.bodybuddy.domain.recommendation.repository;

import com.centerton.bodybuddy.domain.recommendation.entity.Recommendation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, String> {

    Optional<Recommendation> findByMealMealId(String mealId);

    Optional<Recommendation> findByRecommendationIdAndUserUserId(
            String recommendationId,
            String userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select recommendation
            from Recommendation recommendation
            where recommendation.recommendationId = :recommendationId
              and recommendation.user.userId = :userId
            """)
    Optional<Recommendation> findOwnedByIdForUpdate(
            @Param("recommendationId") String recommendationId,
            @Param("userId") String userId
    );
}
