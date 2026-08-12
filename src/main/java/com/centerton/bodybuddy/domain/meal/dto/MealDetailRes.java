package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.meal.entity.ImageSource;
import com.centerton.bodybuddy.domain.meal.entity.MealInputType;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class MealDetailRes {
    private String mealId;
    private MealInputType inputType;
    private ImageSource imageSource;
    private MealStatus status;
    private Long version;
    private OffsetDateTime eatenAt;
    private String photoUrl;
    private List<MealItemRes> items;
    private NutritionSummaryRes nutritionSummary;
    private AnalysisSummaryRes analysis;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
