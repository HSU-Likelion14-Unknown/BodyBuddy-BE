package com.centerton.bodybuddy.domain.recommendation.dto;

import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecisionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationDecisionReq {

    @NotNull(message = "추천 결정값을 입력해 주세요.")
    private RecommendationDecisionType decision;

    private String ingredientId;
}
