package com.centerton.bodybuddy.domain.calendar.controller;

import com.centerton.bodybuddy.domain.calendar.dto.DailyMealsRes;
import com.centerton.bodybuddy.domain.calendar.dto.MonthlyStatsRes;
import com.centerton.bodybuddy.domain.calendar.service.CalendarService;
import com.centerton.bodybuddy.global.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/days/{date}")
    public ResponseEntity<SuccessResponse<DailyMealsRes>> getMealsByDate(
            @RequestHeader("Authorization") String authorization,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        DailyMealsRes response = calendarService.getMealsByDate(authorization, date);
        return ResponseEntity.status(HttpStatus.OK).body(SuccessResponse.from(response));
    }

    @GetMapping("/months/{month}")
    public ResponseEntity<SuccessResponse<MonthlyStatsRes>> getMonthlyStats(
            @RequestHeader("Authorization") String authorization,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month
    ) {
        MonthlyStatsRes response = calendarService.getMonthlyStats(authorization, month);
        return ResponseEntity.status(HttpStatus.OK).body(SuccessResponse.from(response));
    }
}