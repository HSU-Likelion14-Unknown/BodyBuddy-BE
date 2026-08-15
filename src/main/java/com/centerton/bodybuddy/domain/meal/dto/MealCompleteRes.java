package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class MealCompleteRes {
    private String mealId;
    private MealStatus status;
    private OffsetDateTime completedAt;
}
