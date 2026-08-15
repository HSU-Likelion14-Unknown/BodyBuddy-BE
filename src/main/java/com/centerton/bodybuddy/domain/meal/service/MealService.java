package com.centerton.bodybuddy.domain.meal.service;

import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisRunType;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisStatus;
import com.centerton.bodybuddy.domain.analysis.entity.RecognitionResult;
import com.centerton.bodybuddy.domain.analysis.repository.AiAnalysisRunRepository;
import com.centerton.bodybuddy.domain.auth.util.AccessKeyGenerator;
import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.auth.entity.IdempotencyKey;
import com.centerton.bodybuddy.domain.auth.repository.IdempotencyKeyRepository;
import com.centerton.bodybuddy.domain.food.service.FoodCatalogMatch;
import com.centerton.bodybuddy.domain.food.service.FoodMatchingService;
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
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class MealService {

    private static final String TEXT_MEAL_CREATE = "TEXT_MEAL_CREATE";

    private final UserRepository userRepository;
    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final MealNutritionSummaryRepository nutritionSummaryRepository;
    private final AiAnalysisRunRepository analysisRunRepository;
    private final FoodMatchingService foodMatchingService;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional
    public MealAcceptedRes createTextMeal(String authorization, String idempotencyKey,
                                          TextMealCreateReq request) {
        User user = authenticatedUser(authorization);
        requireOnboarding(user);

        String normalizedText = request.getText().trim();
        LocalDateTime eatenAtUtc = request.getEatenAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        String fingerprint = AccessKeyGenerator.hash(
                normalizedText + ":" + request.getEatenAt().toInstant()
        );

        MealAcceptedRes previousResponse = findIdempotentTextMeal(
                idempotencyKey,
                user,
                fingerprint
        );
        if (previousResponse != null) {
            return previousResponse;
        }

        Meal meal = mealRepository.save(Meal.createText(user, normalizedText, eatenAtUtc));
        analysisRunRepository.save(
                AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, fingerprint)
        );
        idempotencyKeyRepository.save(IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .userId(user.getUserId())
                .operation(TEXT_MEAL_CREATE)
                .requestFingerprint(fingerprint)
                .resourceId(meal.getMealId())
                .build());

        return MealAcceptedRes.builder()
                .mealId(meal.getMealId())
                .status(meal.getStatus())
                .build();
    }

    private MealAcceptedRes findIdempotentTextMeal(String idempotencyKey, User user,
                                                    String requestFingerprint) {
        IdempotencyKey record = idempotencyKeyRepository.findById(idempotencyKey).orElse(null);
        if (record == null) {
            return null;
        }
        boolean sameRequest = user.getUserId().equals(record.getUserId())
                && TEXT_MEAL_CREATE.equals(record.getOperation())
                && requestFingerprint.equals(record.getRequestFingerprint())
                && record.getResourceId() != null;
        if (!sameRequest) {
            throw new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED);
        }

        Meal previousMeal = mealRepository.findById(record.getResourceId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED));
        return MealAcceptedRes.builder()
                .mealId(previousMeal.getMealId())
                .status(MealStatus.ANALYZING)
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

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        MealItemCalculation calculation = replaceMealItems(mealId, meal, request.getItems(), now);

        meal.updateEatenAt(request.getEatenAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime());
        LocalDateTime confirmedAt = now;
        meal.confirm(confirmedAt);

        return MealConfirmRes.builder()
                .mealId(meal.getMealId())
                .status(meal.getStatus())
                .items(calculation.items().stream().map(MealItemRes::from).toList())
                .nutritionSummaryStatus(summaryStatus(calculation.items()))
                .nutritionSummary(NutritionSummaryRes.from(calculation.summary()))
                .build();
    }

    @Transactional
    public MealCompleteRes completeMeal(String authorization, String mealId) {
        User user = authenticatedUser(authorization);
        Meal meal = activeMeal(mealId, user.getUserId());
        if (meal.getStatus() != MealStatus.CONFIRMED) {
            throw new BaseException(ErrorResponseCode.MEAL_COMPLETION_CONFLICT);
        }

        LocalDateTime completedAt = LocalDateTime.now(ZoneOffset.UTC);
        meal.complete(completedAt);

        return MealCompleteRes.builder()
                .mealId(meal.getMealId())
                .status(meal.getStatus())
                .completedAt(toUtc(meal.getCompletedAt()))
                .build();
    }

    @Transactional
    public MealItemsUpdateRes updateMealItems(String authorization, String mealId,
                                               MealItemsUpdateReq request) {
        User user = authenticatedUser(authorization);
        Meal meal = activeMeal(mealId, user.getUserId());
        if (meal.getStatus() != MealStatus.CONFIRMED) {
            throw new BaseException(ErrorResponseCode.MEAL_ITEMS_INVALID);
        }

        MealItemCalculation calculation = replaceMealItems(
                mealId,
                meal,
                request.getItems(),
                LocalDateTime.now(ZoneOffset.UTC)
        );

        return MealItemsUpdateRes.builder()
                .mealId(meal.getMealId())
                .status(meal.getStatus())
                .items(calculation.items().stream().map(MealItemRes::from).toList())
                .nutritionSummaryStatus(summaryStatus(calculation.items()))
                .nutritionSummary(NutritionSummaryRes.from(calculation.summary()))
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

    private MealItemCalculation replaceMealItems(String mealId, Meal meal,
                                                  List<MealItemInputReq> requests,
                                                  LocalDateTime calculatedAt) {
        List<MealItem> items = createConfirmedItems(meal, requests);
        NutritionValues totalNutrition = sumNutrition(items);

        mealItemRepository.deleteAllByMealMealId(mealId);
        mealItemRepository.flush();
        List<MealItem> savedItems = mealItemRepository.saveAll(items);

        nutritionSummaryRepository.findById(mealId)
                .ifPresent(nutritionSummaryRepository::delete);
        nutritionSummaryRepository.flush();
        MealNutritionSummary summary = nutritionSummaryRepository.save(
                MealNutritionSummary.builder()
                        .meal(meal)
                        .nutrition(totalNutrition)
                        .basis(NutritionBasis.USER_CONFIRMED)
                        .calculatedAt(calculatedAt)
                        .build()
        );

        return new MealItemCalculation(savedItems, summary);
    }

    private MealItem createConfirmedItem(Meal meal, MealItemInputReq request, int sortOrder) {
        String requestedFoodId = trimToNull(request.getFoodId());
        FoodCatalogMatch catalogMatch = resolveCatalogMatch(requestedFoodId, request.getFoodName());
        NutritionValues nutrition = calculateNutrition(catalogMatch, request);

        return MealItem.builder()
                .mealItemId(UUID.randomUUID().toString())
                .meal(meal)
                .foodId(catalogMatch == null ? null : catalogMatch.food().getFoodId())
                .foodName(request.getFoodName().trim())
                .amount(request.getAmount())
                .amountUnit(request.getUnit().trim())
                .source(requestedFoodId == null
                        ? MealItemSource.USER_ADDED
                        : MealItemSource.USER_EDITED)
                .sortOrder(sortOrder)
                .nutrition(nutrition)
                .build();
    }

    private FoodCatalogMatch resolveCatalogMatch(String requestedFoodId, String foodName) {
        if (requestedFoodId != null) {
            return foodMatchingService.findById(requestedFoodId)
                    .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_ITEMS_INVALID));
        }
        return foodMatchingService.matchByName(foodName).orElse(null);
    }

    private NutritionValues calculateNutrition(FoodCatalogMatch match, MealItemInputReq request) {
        if (match == null || match.nutrition() == null) {
            return null;
        }
        var foodNutrition = match.nutrition();
        if (foodNutrition.getReferenceAmount() == null
                || foodNutrition.getReferenceAmount().signum() <= 0
                || foodNutrition.getReferenceUnit() == null
                || !foodNutrition.getReferenceUnit().equalsIgnoreCase(request.getUnit().trim())) {
            return null;
        }

        BigDecimal ratio = request.getAmount().divide(
                foodNutrition.getReferenceAmount(),
                8,
                RoundingMode.HALF_UP
        );
        return foodNutrition.getNutrition() == null
                ? null
                : scaleNutrition(foodNutrition.getNutrition(), ratio);
    }

    private NutritionValues scaleNutrition(NutritionValues nutrition, BigDecimal ratio) {
        return NutritionValues.builder()
                .caloriesKcal(scale(nutrition.getCaloriesKcal(), ratio))
                .carbohydrateG(scale(nutrition.getCarbohydrateG(), ratio))
                .proteinG(scale(nutrition.getProteinG(), ratio))
                .fatG(scale(nutrition.getFatG(), ratio))
                .fiberG(scale(nutrition.getFiberG(), ratio))
                .sodiumMg(scale(nutrition.getSodiumMg(), ratio))
                .calciumMg(scale(nutrition.getCalciumMg(), ratio))
                .ironMg(scale(nutrition.getIronMg(), ratio))
                .potassiumMg(scale(nutrition.getPotassiumMg(), ratio))
                .vitaminAMcgRae(scale(nutrition.getVitaminAMcgRae(), ratio))
                .vitaminCMg(scale(nutrition.getVitaminCMg(), ratio))
                .build();
    }

    private BigDecimal scale(BigDecimal value, BigDecimal ratio) {
        return value == null ? null : value.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private NutritionValues sumNutrition(List<MealItem> items) {
        List<NutritionValues> values = items.stream()
                .map(MealItem::getNutrition)
                .filter(Objects::nonNull)
                .toList();
        return NutritionValues.builder()
                .caloriesKcal(sum(values, NutritionValues::getCaloriesKcal))
                .carbohydrateG(sum(values, NutritionValues::getCarbohydrateG))
                .proteinG(sum(values, NutritionValues::getProteinG))
                .fatG(sum(values, NutritionValues::getFatG))
                .fiberG(sum(values, NutritionValues::getFiberG))
                .sodiumMg(sum(values, NutritionValues::getSodiumMg))
                .calciumMg(sum(values, NutritionValues::getCalciumMg))
                .ironMg(sum(values, NutritionValues::getIronMg))
                .potassiumMg(sum(values, NutritionValues::getPotassiumMg))
                .vitaminAMcgRae(sum(values, NutritionValues::getVitaminAMcgRae))
                .vitaminCMg(sum(values, NutritionValues::getVitaminCMg))
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

    private NutritionSummaryStatus summaryStatus(List<MealItem> items) {
        return items.stream().allMatch(item -> item.getNutrition() != null)
                ? NutritionSummaryStatus.COMPLETE
                : NutritionSummaryStatus.PARTIAL;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private record MealItemCalculation(
            List<MealItem> items,
            MealNutritionSummary summary
    ) {
    }
}
