package com.centerton.bodybuddy.domain.recommendation.repository;

import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationDecisionRepository
        extends JpaRepository<RecommendationDecision, String> {
}
