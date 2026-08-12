package com.centerton.bodybuddy.domain.meal.service;

import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisRunType;
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

    private static final int POLL_AFTER_MS = 1000;

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
                .analysis(toAnalysisRes(analysisRun))
                .pollAfterMs(POLL_AFTER_MS)
                .build();
    }

    @Transactional(readOnly = true)
    public MealDetailRes getMeal(String authorization, String mealId) {
        User user = authenticatedUser(authorization);
        Meal meal = activeMeal(mealId, user.getUserId());
        AiAnalysisRun analysisRun = analysisRunRepository
                .findFirstByMealMealIdOrderByStartedAtDesc(mealId)
                .orElse(null);

        return MealDetailRes.builder()
                .mealId(meal.getMealId())
                .inputType(meal.getInputType())
                .imageSource(meal.getImageSource())
                .status(meal.getStatus())
                .version(meal.getVersion())
                .eatenAt(toUtc(meal.getEatenAt()))
                .photoUrl(meal.getPhotoObjectKey())
                .items(mealItemRepository.findAllByMealMealIdOrderBySortOrderAsc(mealId)
                        .stream()
                        .map(MealItemRes::from)
                        .toList())
                .nutritionSummary(nutritionSummaryRepository.findById(mealId)
                        .map(NutritionSummaryRes::from)
                        .orElse(null))
                .analysis(analysisRun == null ? null : toAnalysisRes(analysisRun))
                .createdAt(toUtc(meal.getCreatedAt()))
                .updatedAt(toUtc(meal.getUpdatedAt()))
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

    private AnalysisSummaryRes toAnalysisRes(AiAnalysisRun analysisRun) {
        return AnalysisSummaryRes.builder()
                .analysisRunId(analysisRun.getAnalysisRunId())
                .status(analysisRun.getStatus())
                .build();
    }

    private OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
