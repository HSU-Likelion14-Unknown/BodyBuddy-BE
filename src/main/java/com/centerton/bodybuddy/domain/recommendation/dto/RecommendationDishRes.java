package com.centerton.bodybuddy.domain.recommendation.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecommendationDishRes {
    private String dishId;
    private String foodId;
    private String dishName;
    private int rank;
}
