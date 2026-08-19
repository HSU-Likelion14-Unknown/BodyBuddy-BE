package com.centerton.bodybuddy.domain.recommendation.controller;

import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationDecisionReq;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationDecisionRes;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationRes;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecisionType;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationStatus;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationCreationResult;
import com.centerton.bodybuddy.domain.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock private RecommendationService recommendationService;
    @InjectMocks private RecommendationController controller;

    @Test
    void respondsCreatedOnlyWhenCandidateRecommendationIsNew() {
        RecommendationRes response = RecommendationRes.builder()
                .recommendationId("recommendation-id")
                .status(RecommendationStatus.CREATED)
                .build();
        when(recommendationService.create("Bearer key", "idempotency-key", "meal-id"))
                .thenReturn(new RecommendationCreationResult(response, true));

        ResponseEntity<RecommendationRes> result = controller.create(
                "Bearer key", "idempotency-key", "meal-id"
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void respondsOkForNoCandidate() {
        RecommendationRes response = RecommendationRes.builder()
                .recommendationId("recommendation-id")
                .status(RecommendationStatus.NO_CANDIDATE)
                .build();
        when(recommendationService.create("Bearer key", "idempotency-key", "meal-id"))
                .thenReturn(new RecommendationCreationResult(response, true));

        ResponseEntity<RecommendationRes> result = controller.create(
                "Bearer key", "idempotency-key", "meal-id"
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void respondsOkAfterSavingDecision() {
        RecommendationDecisionReq request = new RecommendationDecisionReq(
                RecommendationDecisionType.SKIPPED,
                null
        );
        RecommendationDecisionRes response = RecommendationDecisionRes.builder()
                .recommendationId("recommendation-id")
                .status(RecommendationStatus.SKIPPED)
                .decidedAt(OffsetDateTime.now())
                .build();
        when(recommendationService.decide(
                "Bearer key", "idempotency-key", "recommendation-id", request))
                .thenReturn(response);

        ResponseEntity<RecommendationDecisionRes> result = controller.decide(
                "Bearer key", "idempotency-key", "recommendation-id", request
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void respondsOkWhenReadingSavedDecision() {
        RecommendationDecisionRes response = RecommendationDecisionRes.builder()
                .recommendationId("recommendation-id")
                .status(RecommendationStatus.SELECTED)
                .selectedIngredientId("ingredient-id")
                .decidedAt(OffsetDateTime.now())
                .build();
        when(recommendationService.getDecision("******", "recommendation-id"))
                .thenReturn(response);

        ResponseEntity<RecommendationDecisionRes> result = controller.getDecision(
                "******", "recommendation-id"
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void respondsOkWithRefreshedRecommendation() {
        RecommendationRes response = RecommendationRes.builder()
                .recommendationId("recommendation-id")
                .status(RecommendationStatus.CREATED)
                .build();
        when(recommendationService.refresh(
                "Bearer key", "idempotency-key", "recommendation-id"))
                .thenReturn(response);

        ResponseEntity<RecommendationRes> result = controller.refresh(
                "Bearer key", "idempotency-key", "recommendation-id"
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }
}
