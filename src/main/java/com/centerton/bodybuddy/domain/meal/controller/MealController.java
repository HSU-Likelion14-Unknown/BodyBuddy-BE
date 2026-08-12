package com.centerton.bodybuddy.domain.meal.controller;

import com.centerton.bodybuddy.domain.meal.dto.MealAcceptedRes;
import com.centerton.bodybuddy.domain.meal.dto.MealConfirmRes;
import com.centerton.bodybuddy.domain.meal.dto.MealConfirmReq;
import com.centerton.bodybuddy.domain.meal.dto.MealDetailRes;
import com.centerton.bodybuddy.domain.meal.dto.TextMealCreateReq;
import com.centerton.bodybuddy.domain.meal.service.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meals")
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;

    @PostMapping("/text")
    public ResponseEntity<MealAcceptedRes> createTextMeal(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TextMealCreateReq request
    ) {
        MealAcceptedRes response = mealService.createTextMeal(authorization, idempotencyKey, request);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{mealId}")
    public ResponseEntity<MealDetailRes> getMeal(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String mealId
    ) {
        return ResponseEntity.ok(mealService.getMeal(authorization, mealId));
    }

    @PostMapping("/{mealId}/confirm")
    public ResponseEntity<MealConfirmRes> confirmMeal(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String mealId,
            @Valid @RequestBody MealConfirmReq request
    ) {
        return ResponseEntity.ok(mealService.confirmMeal(authorization, mealId, request));
    }

    @DeleteMapping("/{mealId}")
    public ResponseEntity<Void> deleteMeal(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String mealId
    ) {
        mealService.deleteMeal(authorization, mealId);
        return ResponseEntity.noContent().build();
    }

}
