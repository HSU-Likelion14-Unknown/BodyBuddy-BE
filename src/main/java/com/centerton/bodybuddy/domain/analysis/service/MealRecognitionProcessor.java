package com.centerton.bodybuddy.domain.analysis.service;

import com.centerton.bodybuddy.domain.analysis.client.FoodRecognitionCandidate;
import com.centerton.bodybuddy.domain.analysis.client.FoodRecognitionClient;
import com.centerton.bodybuddy.domain.analysis.client.FoodRecognitionInput;
import com.centerton.bodybuddy.domain.analysis.client.FoodRecognitionResponse;
import com.centerton.bodybuddy.domain.analysis.client.FoodRecognitionResultType;
import com.centerton.bodybuddy.domain.analysis.config.FoodRecognitionProperties;
import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.RecognitionResult;
import com.centerton.bodybuddy.domain.analysis.entity.RecognitionFailureReason;
import com.centerton.bodybuddy.domain.analysis.entity.RecognizedFood;
import com.centerton.bodybuddy.domain.analysis.repository.AiAnalysisRunRepository;
import com.centerton.bodybuddy.domain.food.service.FoodMatchingService;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.MealInputType;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
import com.centerton.bodybuddy.domain.meal.storage.MealImageStorage;
import com.centerton.bodybuddy.domain.meal.storage.StoredMealImage;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MealRecognitionProcessor {

    private final FoodRecognitionClient recognitionClient;
    private final FoodMatchingService foodMatchingService;
    private final MealImageStorage imageStorage;
    private final MealRepository mealRepository;
    private final AiAnalysisRunRepository analysisRunRepository;
    private final FoodRecognitionProperties recognitionProperties;

    @Transactional
    public void process(String mealId, String analysisRunId) {
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_NOT_FOUND));
        AiAnalysisRun run = analysisRunRepository.findById(analysisRunId)
                .filter(candidate -> candidate.getMeal().getMealId().equals(mealId))
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_NOT_FOUND));
        long startedNanos = System.nanoTime();
        run.markRunning();
        try {
            FoodRecognitionResponse response = recognitionClient.recognize(inputOf(meal));
            validate(response);
            RecognitionResult normalized = RecognitionResult.builder()
                    .foods(response.candidates().stream().map(this::match).toList())
                    .build();
            if (response.resultType() == FoodRecognitionResultType.NO_FOOD) {
                failRecognized(
                        run,
                        response,
                        normalized,
                        RecognitionFailureReason.NO_FOOD,
                        startedNanos
                );
                meal.markFailed();
                return;
            }
            if (highestConfidence(response).compareTo(
                    recognitionProperties.getMinimumConfidence()
            ) < 0) {
                failRecognized(
                        run,
                        response,
                        normalized,
                        RecognitionFailureReason.LOW_CONFIDENCE,
                        startedNanos
                );
                meal.markFailed();
                return;
            }
            run.succeed(
                    normalized,
                    response.provider(),
                    response.model(),
                    response.promptVersion(),
                    response.providerResponseId(),
                    elapsedMillis(startedNanos),
                    response.inputTokens(),
                    response.outputTokens()
            );
            meal.markReviewRequired();
        } catch (Exception exception) {
            String code = exception instanceof BaseException baseException
                    ? baseException.getBaseResponseCode().getCode()
                    : ErrorResponseCode.AI_SERVICE_UNAVAILABLE.getCode();
            String message = exception instanceof BaseException baseException
                    ? baseException.getBaseResponseCode().getMessage()
                    : exception.getMessage();
            run.fail(code, message, elapsedMillis(startedNanos));
            meal.markFailed();
        }
    }

    private FoodRecognitionInput inputOf(Meal meal) {
        if (meal.getInputType() == MealInputType.TEXT) {
            return FoodRecognitionInput.text(meal.getDirectInputText());
        }
        StoredMealImage image = imageStorage.load(meal.getPhotoObjectKey());
        return FoodRecognitionInput.image(image.bytes(), image.mediaType());
    }

    private RecognizedFood match(FoodRecognitionCandidate candidate) {
        String foodId = foodMatchingService.matchByName(candidate.foodName())
                .map(match -> match.food().getFoodId())
                .orElse(null);
        return RecognizedFood.builder()
                .foodId(foodId)
                .foodName(candidate.foodName().trim())
                .confidence(candidate.confidence())
                .build();
    }

    private void validate(FoodRecognitionResponse response) {
        if (response == null
                || response.resultType() == null
                || response.candidates() == null
                || (response.resultType() == FoodRecognitionResultType.FOOD
                && response.candidates().isEmpty())
                || (response.resultType() == FoodRecognitionResultType.NO_FOOD
                && !response.candidates().isEmpty())
                || isBlank(response.provider())
                || isBlank(response.model())
                || isBlank(response.promptVersion())
                || response.candidates().stream().anyMatch(this::invalidCandidate)) {
            throw new BaseException(ErrorResponseCode.AI_BAD_RESPONSE);
        }
    }

    private boolean invalidCandidate(FoodRecognitionCandidate candidate) {
        if (candidate == null || isBlank(candidate.foodName()) || candidate.confidence() == null) {
            return true;
        }
        return candidate.confidence().compareTo(BigDecimal.ZERO) < 0
                || candidate.confidence().compareTo(BigDecimal.ONE) > 0;
    }

    private BigDecimal highestConfidence(FoodRecognitionResponse response) {
        return response.candidates().stream()
                .map(FoodRecognitionCandidate::confidence)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private void failRecognized(AiAnalysisRun run, FoodRecognitionResponse response,
                                RecognitionResult normalized,
                                RecognitionFailureReason reason,
                                long startedNanos) {
        run.failWithResponse(
                normalized,
                response.provider(),
                response.model(),
                response.promptVersion(),
                response.providerResponseId(),
                reason.getErrorCode(),
                reason.getMessage(),
                elapsedMillis(startedNanos),
                response.inputTokens(),
                response.outputTokens()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int elapsedMillis(long startedNanos) {
        long millis = (System.nanoTime() - startedNanos) / 1_000_000L;
        return (int) Math.min(millis, Integer.MAX_VALUE);
    }
}
