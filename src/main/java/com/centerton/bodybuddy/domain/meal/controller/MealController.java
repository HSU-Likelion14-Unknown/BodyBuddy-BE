package com.centerton.bodybuddy.domain.meal.controller;

import com.centerton.bodybuddy.domain.meal.dto.MealAcceptedRes;
import com.centerton.bodybuddy.domain.meal.dto.MealConfirmRes;
import com.centerton.bodybuddy.domain.meal.dto.MealDetailRes;
import com.centerton.bodybuddy.domain.meal.dto.TextMealCreateReq;
import com.centerton.bodybuddy.domain.meal.service.MealService;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.SuccessResponse;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
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
    public ResponseEntity<SuccessResponse<MealAcceptedRes>> createTextMeal(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TextMealCreateReq request
    ) {
        MealAcceptedRes response = mealService.createTextMeal(authorization, idempotencyKey, request);
        return ResponseEntity.accepted().body(SuccessResponse.accepted(response));
    }

    @GetMapping("/{mealId}")
    public ResponseEntity<SuccessResponse<MealDetailRes>> getMeal(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String mealId
    ) {
        return ResponseEntity.ok(SuccessResponse.from(mealService.getMeal(authorization, mealId)));
    }

    @PostMapping("/{mealId}/confirm")
    public ResponseEntity<SuccessResponse<MealConfirmRes>> confirmMeal(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("If-Match") String ifMatch,
            @PathVariable String mealId
    ) {
        long expectedVersion = parseVersion(ifMatch);
        return ResponseEntity.ok(SuccessResponse.from(
                mealService.confirmMeal(authorization, mealId, expectedVersion)
        ));
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

    private long parseVersion(String ifMatch) {
        try {
            return Long.parseLong(ifMatch.replace("\"", "").trim());
        } catch (NumberFormatException exception) {
            throw new BaseException(ErrorResponseCode.INVALID_INPUT_VALUE);
        }
    }
}
