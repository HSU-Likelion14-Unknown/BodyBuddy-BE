package com.centerton.bodybuddy.domain.recommendation.repository;

import com.centerton.bodybuddy.domain.recommendation.entity.Recommendation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, String> {

    Optional<Recommendation> findByMealMealId(String mealId);

    Optional<Recommendation> findFirstByUserUserIdOrderByCreatedAtDescRecommendationIdDesc(
            String userId
    );

    Optional<Recommendation> findByRecommendationIdAndUserUserId(
            String recommendationId,
            String userId
    );

    @Query("""
            select recommendation
            from Recommendation recommendation
            where recommendation.user.userId = :userId
              and recommendation.recommendationDate >= :startDate
              and recommendation.recommendationDate < :endDate
            order by recommendation.recommendationDate
            """)
    List<Recommendation> findMonthlyRecommendations(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select recommendation
            from Recommendation recommendation
            where recommendation.user.userId = :userId
              and recommendation.createdAt >= :startAt
              and recommendation.createdAt < :endAt
            order by recommendation.createdAt, recommendation.recommendationId
            """)
    List<Recommendation> findCreatedBetween(
            @Param("userId") String userId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
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
