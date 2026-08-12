package com.centerton.bodybuddy.domain.meal.service;

import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisRunType;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisStatus;
import com.centerton.bodybuddy.domain.analysis.entity.RecognitionResult;
import com.centerton.bodybuddy.domain.analysis.repository.AiAnalysisRunRepository;
import com.centerton.bodybuddy.domain.auth.util.AccessKeyGenerator;
import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.meal.dto.*;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import com.centerton.bodybuddy.domain.meal.repository.MealItemRepository;
import com.centerton.bodybuddy.domain.meal.repository.MealNutritionSummaryRepository;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class MealService {

    private final UserRepository userRepository;
    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final MealNutritionSummaryRepository nutritionSummaryRepository;
    private final AiAnalysisRunRepository analysisRunRepository;

    @Transactional
    public MealAcceptedRes createTextMeal(String authorization, String idempotencyKey,
                                          TextMealCreateReq request) {
        User user = authenticatedUser(authorization);
        requireOnboarding(user);

        String normalizedText = request.getText().trim();
        LocalDateTime eatenAtUtc = request.getEatenAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        Meal meal = mealRepository.save(Meal.createText(user, normalizedText, eatenAtUtc));
        String fingerprint = AccessKeyGenerator.hash(
                idempotencyKey + ":" + normalizedText + ":" + request.getEatenAt()
        );
        AiAnalysisRun analysisRun = analysisRunRepository.save(
                AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, fingerprint)
        );

        return MealAcceptedRes.builder()
                .mealId(meal.getMealId())
                .status(meal.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public MealDetailRes getMeal(String authorization, String mealId) {
        User user = authenticatedUser(authorization);
        Meal meal = activeMeal(mealId, user.getUserId());
        RecognitionResult recognitionResult = analysisRunRepository
                .findFirstByMealMealIdAndStatusOrderByStartedAtDesc(mealId, AnalysisStatus.SUCCEEDED)
                .map(AiAnalysisRun::getNormalizedResponse)
                .orElse(null);

        return MealDetailRes.builder()
                .mealId(meal.getMealId())
                .status(meal.getStatus())
                .eatenAt(toUtc(meal.getEatenAt()))
                .recognizedItems(recognitionResult == null || recognitionResult.getFoods() == null
                        ? null
                        : recognitionResult.getFoods().stream().map(RecognizedItemRes::from).toList())
                .items(mealItemRepository.findAllByMealMealIdOrderBySortOrderAsc(mealId)
                        .stream()
                        .map(MealItemRes::from)
                        .toList())
                .nutritionSummary(nutritionSummaryRepository.findById(mealId)
                        .map(NutritionSummaryRes::from)
                        .orElse(null))
                .recommendation(null)
                .build();
    }

    @Transactional
    public MealConfirmRes confirmMeal(String authorization, String mealId, long expectedVersion) {
        User user = authenticatedUser(authorization);
        Meal meal = activeMeal(mealId, user.getUserId());
        requireVersion(meal, expectedVersion);
        if (meal.getStatus() != MealStatus.REVIEW_REQUIRED) {
            throw new BaseException(ErrorResponseCode.INVALID_MEAL_STATUS);
        }

        LocalDateTime confirmedAt = LocalDateTime.now(ZoneOffset.UTC);
        meal.confirm(confirmedAt);
        mealRepository.flush();

        return MealConfirmRes.builder()
                .mealId(meal.getMealId())
                .status(meal.getStatus())
                .version(meal.getVersion())
                .confirmedAt(toUtc(confirmedAt))
                .build();
    }

    @Transactional
    public void deleteMeal(String authorization, String mealId) {
        User user = authenticatedUser(authorization);
        Meal meal = mealRepository.findByMealIdAndUserUserId(mealId, user.getUserId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_NOT_FOUND));
        if (meal.getDeletedAt() == null) {
            meal.delete(LocalDateTime.now(ZoneOffset.UTC));
        }
    }

    private User authenticatedUser(String authorization) {
        return AuthValidator.validateAndGetUser(authorization, userRepository);
    }

    private void requireOnboarding(User user) {
        if (user.getOnboardingCompletedAt() == null) {
            throw new BaseException(ErrorResponseCode.ONBOARDING_NOT_COMPLETED);
        }
    }

    private Meal activeMeal(String mealId, String userId) {
        return mealRepository.findByMealIdAndUserUserIdAndDeletedAtIsNull(mealId, userId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_NOT_FOUND));
    }

    private void requireVersion(Meal meal, long expectedVersion) {
        if (meal.getVersion() == null || meal.getVersion() != expectedVersion) {
            throw new BaseException(ErrorResponseCode.MEAL_VERSION_CONFLICT);
        }
    }

    private OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
