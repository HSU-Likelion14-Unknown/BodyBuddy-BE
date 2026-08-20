package com.centerton.bodybuddy.domain.calendar.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CalendarMealImageUpdateRes {
    private String mealId;
    private String photoUrl;
}
