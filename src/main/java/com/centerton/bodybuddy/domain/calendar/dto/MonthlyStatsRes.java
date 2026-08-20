package com.centerton.bodybuddy.domain.calendar.dto;

import com.centerton.bodybuddy.domain.calendar.model.CalendarMealStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class MonthlyStatsRes {
    private String month;
    private BigDecimal totalCalories;
    private BigDecimal averageCalories;
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
        private LocalDateTime eatenAt;
        private CalendarMealStatus status;
    }
}