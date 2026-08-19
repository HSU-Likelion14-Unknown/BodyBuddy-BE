package com.centerton.bodybuddy.domain.recommendation.controller;

import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationDecisionReq;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationDecisionRes;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationRes;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationStatus;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationCreationResult;
import com.centerton.bodybuddy.domain.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping("/meals/{mealId}/recommendations")
    public ResponseEntity<RecommendationRes> create(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String mealId
    ) {
        RecommendationCreationResult result = recommendationService.create(
                authorization,
                idempotencyKey,
                mealId
        );
        boolean created = result.createdNow()
                && result.response().getStatus() == RecommendationStatus.CREATED;
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }

    @PostMapping("/recommendations/{recommendationId}/decision")
    public ResponseEntity<RecommendationDecisionRes> decide(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String recommendationId,
            @Valid @RequestBody RecommendationDecisionReq request
    ) {
        return ResponseEntity.ok(recommendationService.decide(
                authorization,
                idempotencyKey,
                recommendationId,
                request
        ));
    }

    @PostMapping("/recommendations/{recommendationId}/refresh")
    public ResponseEntity<RecommendationRes> refresh(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String recommendationId
    ) {
        return ResponseEntity.ok(recommendationService.refresh(
                authorization,
                idempotencyKey,
                recommendationId
        ));
    }
}
