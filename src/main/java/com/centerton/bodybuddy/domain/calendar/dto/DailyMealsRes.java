package com.centerton.bodybuddy.domain.calendar.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class DailyMealsRes {
    private String date;
    private List<MealInfo> meals;

    @Getter
    @Builder
    public static class MealInfo {
        private String mealId;
        private String directInputText;
        private String photoUrl;
        private LocalDateTime eatenAt;
        private BigDecimal calories;
        private BigDecimal carbohydrate;
        private BigDecimal protein;
        private BigDecimal fat;
        private String recommendedDishName;
    }
}