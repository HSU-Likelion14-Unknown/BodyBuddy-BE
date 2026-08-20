package com.centerton.bodybuddy.domain.calendar.dto;

import com.centerton.bodybuddy.domain.calendar.model.CalendarMealStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class MonthlyStatsRes {
    private String month;
    private BigDecimal totalCalories;
    private BigDecimal averageCalories;
    private BigDecimal averageCarbohydrate;
    private BigDecimal averageProtein;
    private BigDecimal averageFat;
    private int recordedDays;
    private List<DayStatus> days;

    @Getter
    @Builder
    public static class DayStatus {
        private String date;
        private int mealCount;
        private int selectedRecommendationCount;
        private int unselectedRecommendationCount;
        private List<MealRecord> records;
    }

    @Getter
    @Builder
    public static class MealRecord {
        private String mealId;
        private OffsetDateTime eatenAt;
        private CalendarMealStatus status;
    }
}
