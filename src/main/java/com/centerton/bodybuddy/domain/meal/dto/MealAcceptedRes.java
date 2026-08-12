package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealAcceptedRes {
    private String mealId;
    private MealStatus status;
    private AnalysisSummaryRes analysis;
    private int pollAfterMs;
}
