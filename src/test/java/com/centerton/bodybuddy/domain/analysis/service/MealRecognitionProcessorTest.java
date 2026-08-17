package com.centerton.bodybuddy.domain.analysis.service;

import com.centerton.bodybuddy.domain.analysis.client.FoodRecognitionCandidate;
import com.centerton.bodybuddy.domain.analysis.client.FoodRecognitionClient;
import com.centerton.bodybuddy.domain.analysis.client.FoodRecognitionInput;
import com.centerton.bodybuddy.domain.analysis.client.FoodRecognitionResponse;
import com.centerton.bodybuddy.domain.analysis.client.FoodRecognitionResultType;
import com.centerton.bodybuddy.domain.analysis.config.FoodRecognitionProperties;
import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisRunType;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisStatus;
import com.centerton.bodybuddy.domain.analysis.entity.RecognitionFailureReason;
import com.centerton.bodybuddy.domain.analysis.repository.AiAnalysisRunRepository;
import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.service.FoodCatalogMatch;
import com.centerton.bodybuddy.domain.food.service.FoodMatchingService;
import com.centerton.bodybuddy.domain.meal.entity.ImageSource;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
import com.centerton.bodybuddy.domain.meal.storage.MealImageStorage;
import com.centerton.bodybuddy.domain.meal.storage.StoredMealImage;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealRecognitionProcessorTest {

    @Mock private FoodRecognitionClient recognitionClient;
    @Mock private FoodMatchingService foodMatchingService;
    @Mock private MealImageStorage imageStorage;
    @Mock private MealRepository mealRepository;
    @Mock private AiAnalysisRunRepository analysisRunRepository;

    private MealRecognitionProcessor processor;
    private User user;

    @BeforeEach
    void setUp() {
        FoodRecognitionProperties properties = new FoodRecognitionProperties();
        properties.setMinimumConfidence(new BigDecimal("0.60"));
        processor = new MealRecognitionProcessor(
                recognitionClient,
                foodMatchingService,
                imageStorage,
                mealRepository,
                analysisRunRepository,
                properties
        );
        user = User.builder().userId("user-id").accessKeyHash("hash").build();
    }

    @Test
    void matchesRecognizedNamesAndKeepsUnmatchedFoodAsNull() {
        Meal meal = Meal.createText(user, "두부와 특별식", LocalDateTime.now());
        AiAnalysisRun run = AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, "fingerprint");
        persisted(meal, run);
        when(recognitionClient.recognize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(
                        new FoodRecognitionCandidate("두부", new BigDecimal("0.95")),
                        new FoodRecognitionCandidate("엄마표 특별식", new BigDecimal("0.60"))
                ));
        Food food = Food.builder()
                .foodId("food-id")
                .canonicalName("두부")
                .normalizedName("두부")
                .active(true)
                .build();
        when(foodMatchingService.matchByName("두부"))
                .thenReturn(Optional.of(new FoodCatalogMatch(food, null)));
        when(foodMatchingService.matchByName("엄마표 특별식"))
                .thenReturn(Optional.empty());

        processor.process(meal.getMealId(), run.getAnalysisRunId());

        assertThat(meal.getStatus()).isEqualTo(MealStatus.REVIEW_REQUIRED);
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.SUCCEEDED);
        assertThat(run.getNormalizedResponse().getFoods()).hasSize(2);
        assertThat(run.getNormalizedResponse().getFoods().getFirst().getFoodId())
                .isEqualTo("food-id");
        assertThat(run.getNormalizedResponse().getFoods().get(1).getFoodId()).isNull();
    }

    @Test
    void loadsStoredBytesForImageRecognition() {
        Meal meal = Meal.createImage(
                user,
                ImageSource.GALLERY,
                "2026/08/image.png",
                LocalDateTime.now()
        );
        AiAnalysisRun run = AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, "fingerprint");
        persisted(meal, run);
        byte[] bytes = new byte[]{1, 2, 3};
        when(imageStorage.load("2026/08/image.png"))
                .thenReturn(new StoredMealImage(bytes, "image/png"));
        when(recognitionClient.recognize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(new FoodRecognitionCandidate(
                        "김치찌개",
                        new BigDecimal("0.85")
                )));
        when(foodMatchingService.matchByName("김치찌개")).thenReturn(Optional.empty());

        processor.process(meal.getMealId(), run.getAnalysisRunId());

        ArgumentCaptor<FoodRecognitionInput> inputCaptor =
                ArgumentCaptor.forClass(FoodRecognitionInput.class);
        org.mockito.Mockito.verify(recognitionClient).recognize(inputCaptor.capture());
        assertThat(inputCaptor.getValue().imageBytes()).isEqualTo(bytes);
        assertThat(inputCaptor.getValue().imageMediaType()).isEqualTo("image/png");
        assertThat(meal.getStatus()).isEqualTo(MealStatus.REVIEW_REQUIRED);
    }

    @Test
    void failsWhenAllCandidatesAreBelowMinimumConfidence() {
        Meal meal = Meal.createText(user, "모호한 음식", LocalDateTime.now());
        AiAnalysisRun run = AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, "fingerprint");
        persisted(meal, run);
        when(recognitionClient.recognize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(
                        new FoodRecognitionCandidate("죽", new BigDecimal("0.59")),
                        new FoodRecognitionCandidate("수프", new BigDecimal("0.40"))
                ));
        when(foodMatchingService.matchByName("죽")).thenReturn(Optional.empty());
        when(foodMatchingService.matchByName("수프")).thenReturn(Optional.empty());

        processor.process(meal.getMealId(), run.getAnalysisRunId());

        assertThat(meal.getStatus()).isEqualTo(MealStatus.FAILED);
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(run.getErrorCode())
                .isEqualTo(RecognitionFailureReason.LOW_CONFIDENCE.getErrorCode());
        assertThat(run.getNormalizedResponse().getFoods()).hasSize(2);
        assertThat(run.getProvider()).isEqualTo("FAKE");
    }

    @Test
    void acceptsCandidateAtMinimumConfidenceBoundary() {
        Meal meal = Meal.createText(user, "죽", LocalDateTime.now());
        AiAnalysisRun run = AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, "fingerprint");
        persisted(meal, run);
        when(recognitionClient.recognize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(new FoodRecognitionCandidate(
                        "죽",
                        new BigDecimal("0.60")
                )));
        when(foodMatchingService.matchByName("죽")).thenReturn(Optional.empty());

        processor.process(meal.getMealId(), run.getAnalysisRunId());

        assertThat(meal.getStatus()).isEqualTo(MealStatus.REVIEW_REQUIRED);
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.SUCCEEDED);
    }

    @Test
    void keepsLowConfidenceCandidateWhenAnotherCandidatePassesThreshold() {
        Meal meal = Meal.createText(user, "밥과 모호한 반찬", LocalDateTime.now());
        AiAnalysisRun run = AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, "fingerprint");
        persisted(meal, run);
        when(recognitionClient.recognize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(
                        new FoodRecognitionCandidate("밥", new BigDecimal("0.90")),
                        new FoodRecognitionCandidate("나물", new BigDecimal("0.30"))
                ));
        when(foodMatchingService.matchByName("밥")).thenReturn(Optional.empty());
        when(foodMatchingService.matchByName("나물")).thenReturn(Optional.empty());

        processor.process(meal.getMealId(), run.getAnalysisRunId());

        assertThat(meal.getStatus()).isEqualTo(MealStatus.REVIEW_REQUIRED);
        assertThat(run.getNormalizedResponse().getFoods())
                .extracting("foodName")
                .containsExactly("밥", "나물");
    }

    @Test
    void failsAsNoFoodWithoutMatchingCandidates() {
        Meal meal = Meal.createText(user, "음식이 아닌 입력", LocalDateTime.now());
        AiAnalysisRun run = AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, "fingerprint");
        persisted(meal, run);
        when(recognitionClient.recognize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(noFoodResponse());

        processor.process(meal.getMealId(), run.getAnalysisRunId());

        assertThat(meal.getStatus()).isEqualTo(MealStatus.FAILED);
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(run.getErrorCode())
                .isEqualTo(RecognitionFailureReason.NO_FOOD.getErrorCode());
        assertThat(run.getNormalizedResponse().getFoods()).isEmpty();
        verify(foodMatchingService, never()).matchByName(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void marksMealAndRunFailedWhenClientFails() {
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        AiAnalysisRun run = AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, "fingerprint");
        persisted(meal, run);
        when(recognitionClient.recognize(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("temporarily unavailable"));

        processor.process(meal.getMealId(), run.getAnalysisRunId());

        assertThat(meal.getStatus()).isEqualTo(MealStatus.FAILED);
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(run.getErrorCode()).isEqualTo("AI_UNAVAILABLE");
    }

    @Test
    void marksInvalidProviderResponseWithStableFailureCode() {
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        AiAnalysisRun run = AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, "fingerprint");
        persisted(meal, run);
        when(recognitionClient.recognize(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BaseException(ErrorResponseCode.AI_BAD_RESPONSE));

        processor.process(meal.getMealId(), run.getAnalysisRunId());

        assertThat(meal.getStatus()).isEqualTo(MealStatus.FAILED);
        assertThat(run.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(run.getErrorCode())
                .isEqualTo(RecognitionFailureReason.INVALID_RESPONSE.getErrorCode());
    }

    private FoodRecognitionResponse response(FoodRecognitionCandidate... candidates) {
        return new FoodRecognitionResponse(
                FoodRecognitionResultType.FOOD,
                List.of(candidates),
                "FAKE",
                "fake-v1",
                "prompt-v1",
                "response-id",
                null,
                null
        );
    }

    private FoodRecognitionResponse noFoodResponse() {
        return new FoodRecognitionResponse(
                FoodRecognitionResultType.NO_FOOD,
                List.of(),
                "FAKE",
                "fake-v1",
                "prompt-v2",
                "response-id",
                null,
                null
        );
    }

    private void persisted(Meal meal, AiAnalysisRun run) {
        when(mealRepository.findById(meal.getMealId())).thenReturn(Optional.of(meal));
        when(analysisRunRepository.findById(run.getAnalysisRunId())).thenReturn(Optional.of(run));
    }
}
