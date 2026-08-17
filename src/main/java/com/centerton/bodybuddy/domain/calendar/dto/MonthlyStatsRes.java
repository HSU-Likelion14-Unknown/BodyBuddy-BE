package com.centerton.bodybuddy.domain.calendar.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MonthlyStatsRes {
    private String month;
    private BigDecimal totalCalories;
    private BigDecimal averageCalories;
    private int recordedDays;
}