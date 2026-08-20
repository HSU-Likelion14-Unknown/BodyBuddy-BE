package com.centerton.bodybuddy.domain.calendar.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
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
    }
}