package com.centerton.bodybuddy.domain.recommendation.dto;

import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class RecommendationDecisionRes {
    private String recommendationId;
    private RecommendationStatus status;
    private String selectedIngredientId;
    private OffsetDateTime decidedAt;
}
