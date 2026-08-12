package com.centerton.bodybuddy.domain.meal.service;

import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisRunType;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisStatus;
import com.centerton.bodybuddy.domain.analysis.entity.RecognitionResult;
import com.centerton.bodybuddy.domain.analysis.repository.AiAnalysisRunRepository;
import com.centerton.bodybuddy.domain.auth.util.AccessKeyGenerator;
import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.food.entity.FoodNutrition;
import com.centerton.bodybuddy.domain.food.repository.FoodNutritionRepository;
import com.centerton.bodybuddy.domain.meal.dto.*;
import com.centerton.bodybuddy.domain.meal.entity.*;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class MealService {

    private final UserRepository userRepository;
    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final MealNutritionSummaryRepository nutritionSummaryRepository;
    private final AiAnalysisRunRepository analysisRunRepository;
    private final FoodNutritionRepository foodNutritionRepository;

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
        analysisRunRepository.save(
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
    public MealConfirmRes confirmMeal(String authorization, String mealId, MealConfirmReq request) {
        User user = authenticatedUser(authorization);
        Meal meal = activeMeal(mealId, user.getUserId());
        if (meal.getStatus() != MealStatus.REVIEW_REQUIRED) {
            throw new BaseException(ErrorResponseCode.INVALID_MEAL_STATUS);
        }

        List<MealItem> items = createConfirmedItems(meal, request.getItems());
        NutritionValues totalNutrition = sumNutrition(items);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        mealItemRepository.deleteAllByMealMealId(mealId);
        List<MealItem> savedItems = mealItemRepository.saveAll(items);
        nutritionSummaryRepository.findById(mealId).ifPresent(nutritionSummaryRepository::delete);

        MealNutritionSummary summary = nutritionSummaryRepository.save(
                MealNutritionSummary.builder()
                        .meal(meal)
                        .nutrition(totalNutrition)
                        .basis(NutritionBasis.USER_CONFIRMED)
                        .calculatedAt(now)
                        .build()
        );

        meal.updateEatenAt(request.getEatenAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime());
        LocalDateTime confirmedAt = now;
        meal.confirm(confirmedAt);

        return MealConfirmRes.builder()
                .mealId(meal.getMealId())
                .status(meal.getStatus())
                .items(savedItems.stream().map(MealItemRes::from).toList())
                .nutritionSummary(NutritionSummaryRes.from(summary))
                .build();
    }

    @Transactional
    public void deleteMeal(String authorization, String mealId) {
        User user = authenticatedUser(authorization);
        Meal meal = mealRepository.findByMealIdAndUserUserId(mealId, user.getUserId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_NOT_FOUND));
        analysisRunRepository.deleteAllByMealMealId(mealId);
        mealItemRepository.deleteAllByMealMealId(mealId);
        nutritionSummaryRepository.findById(mealId).ifPresent(nutritionSummaryRepository::delete);
        mealRepository.delete(meal);
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
        return mealRepository.findByMealIdAndUserUserId(mealId, userId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_NOT_FOUND));
    }

    private List<MealItem> createConfirmedItems(Meal meal, List<MealItemInputReq> requests) {
        return java.util.stream.IntStream.range(0, requests.size())
                .mapToObj(index -> createConfirmedItem(meal, requests.get(index), index))
                .toList();
    }

    private MealItem createConfirmedItem(Meal meal, MealItemInputReq request, int sortOrder) {
        FoodNutrition foodNutrition = foodNutritionRepository.findById(request.getFoodId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_ITEMS_INVALID));
        if (!foodNutrition.getReferenceUnit().equalsIgnoreCase(request.getUnit().trim())) {
            throw new BaseException(ErrorResponseCode.MEAL_ITEMS_INVALID);
        }

        BigDecimal ratio = request.getAmount().divide(
                foodNutrition.getReferenceAmount(),
                8,
                RoundingMode.HALF_UP
        );
        NutritionValues nutrition = scaleNutrition(foodNutrition.getNutrition(), ratio);

        return MealItem.builder()
                .mealItemId(UUID.randomUUID().toString())
                .meal(meal)
                .foodId(request.getFoodId())
                .foodName(request.getFoodName().trim())
                .amount(request.getAmount())
                .amountUnit(request.getUnit().trim())
                .source(MealItemSource.USER_EDITED)
                .sortOrder(sortOrder)
                .nutrition(nutrition)
                .build();
    }

    private NutritionValues scaleNutrition(NutritionValues nutrition, BigDecimal ratio) {
        if (nutrition == null) {
            throw new BaseException(ErrorResponseCode.MEAL_ITEMS_INVALID);
        }
        return NutritionValues.builder()
                .caloriesKcal(scale(nutrition.getCaloriesKcal(), ratio))
                .carbohydrateG(scale(nutrition.getCarbohydrateG(), ratio))
                .proteinG(scale(nutrition.getProteinG(), ratio))
                .fatG(scale(nutrition.getFatG(), ratio))
                .fiberG(scale(nutrition.getFiberG(), ratio))
                .sodiumMg(scale(nutrition.getSodiumMg(), ratio))
                .build();
    }

    private BigDecimal scale(BigDecimal value, BigDecimal ratio) {
        return value == null ? null : value.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private NutritionValues sumNutrition(List<MealItem> items) {
        List<NutritionValues> values = items.stream().map(MealItem::getNutrition).toList();
        return NutritionValues.builder()
                .caloriesKcal(sum(values, NutritionValues::getCaloriesKcal))
                .carbohydrateG(sum(values, NutritionValues::getCarbohydrateG))
                .proteinG(sum(values, NutritionValues::getProteinG))
                .fatG(sum(values, NutritionValues::getFatG))
                .fiberG(sum(values, NutritionValues::getFiberG))
                .sodiumMg(sum(values, NutritionValues::getSodiumMg))
                .build();
    }

    private BigDecimal sum(List<NutritionValues> values,
                           Function<NutritionValues, BigDecimal> extractor) {
        return values.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal::add)
                .orElse(null);
    }

    private OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
