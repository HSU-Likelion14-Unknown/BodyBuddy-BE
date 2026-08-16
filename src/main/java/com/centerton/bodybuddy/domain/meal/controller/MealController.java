package com.centerton.bodybuddy.domain.meal.controller;

import com.centerton.bodybuddy.domain.meal.dto.MealAcceptedRes;
import com.centerton.bodybuddy.domain.meal.dto.MealCompleteRes;
import com.centerton.bodybuddy.domain.meal.dto.MealConfirmRes;
import com.centerton.bodybuddy.domain.meal.dto.MealConfirmReq;
import com.centerton.bodybuddy.domain.meal.dto.MealDetailRes;
import com.centerton.bodybuddy.domain.meal.dto.MealItemsUpdateReq;
import com.centerton.bodybuddy.domain.meal.dto.MealItemsUpdateRes;
import com.centerton.bodybuddy.domain.meal.dto.RecognitionCandidatesRes;
import com.centerton.bodybuddy.domain.meal.dto.TextMealCreateReq;
import com.centerton.bodybuddy.domain.meal.service.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;

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

    @PostMapping(value = "/images", consumes = "multipart/form-data")
    public ResponseEntity<MealAcceptedRes> createImageMeal(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("image") MultipartFile image,
            @RequestParam("eatenAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime eatenAt
    ) {
        MealAcceptedRes response = mealService.createImageMeal(
                authorization,
                idempotencyKey,
                image,
                eatenAt
        );
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{mealId}/recognition-candidates")
    public ResponseEntity<RecognitionCandidatesRes> getRecognitionCandidates(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String mealId
    ) {
        return ResponseEntity.ok(mealService.getRecognitionCandidates(authorization, mealId));
    }

    @PostMapping("/{mealId}/recognition/retry")
    public ResponseEntity<MealAcceptedRes> retryRecognition(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String mealId
    ) {
        return ResponseEntity.accepted().body(
                mealService.retryRecognition(authorization, idempotencyKey, mealId)
        );
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

    @PostMapping("/{mealId}/complete")
    public ResponseEntity<MealCompleteRes> completeMeal(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String mealId
    ) {
        return ResponseEntity.ok(mealService.completeMeal(authorization, mealId));
    }

    @PutMapping("/{mealId}/items")
    public ResponseEntity<MealItemsUpdateRes> updateMealItems(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String mealId,
            @Valid @RequestBody MealItemsUpdateReq request
    ) {
        return ResponseEntity.ok(mealService.updateMealItems(authorization, mealId, request));
    }

    @DeleteMapping("/{mealId}")
    public ResponseEntity<Void> deleteMeal(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String mealId
    ) {
        mealService.deleteMeal(authorization, mealId);
        return ResponseEntity.noContent().build();
    }

}
