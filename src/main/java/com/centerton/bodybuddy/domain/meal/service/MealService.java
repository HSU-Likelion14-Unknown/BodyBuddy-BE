package com.centerton.bodybuddy.domain.meal.service;

import com.centerton.bodybuddy.domain.analysis.client.FoodNutritionEstimationClient;
import com.centerton.bodybuddy.domain.analysis.client.FoodNutritionEstimationInput;
import com.centerton.bodybuddy.domain.analysis.client.FoodNutritionEstimationResponse;
import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisRunType;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisStatus;
import com.centerton.bodybuddy.domain.analysis.entity.RecognitionResult;
import com.centerton.bodybuddy.domain.analysis.repository.AiAnalysisRunRepository;
import com.centerton.bodybuddy.domain.analysis.service.MealRecognitionRequestedEvent;
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
import com.centerton.bodybuddy.domain.meal.storage.MealImageStorage;
import com.centerton.bodybuddy.domain.meal.storage.ValidatedMealImage;
import com.centerton.bodybuddy.domain.recommendation.service.RecommendationQueryService;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.multipart.MultipartFile;

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
@Slf4j
public class MealService {

    private static final String TEXT_MEAL_CREATE = "TEXT_MEAL_CREATE";
    private static final String IMAGE_MEAL_CREATE = "IMAGE_MEAL_CREATE";
    private static final String RECOGNITION_RETRY = "RECOGNITION_RETRY";

    private final UserRepository userRepository;
    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final MealNutritionSummaryRepository nutritionSummaryRepository;
    private final AiAnalysisRunRepository analysisRunRepository;
    private final FoodMatchingService foodMatchingService;
    private final FoodNutritionEstimationClient nutritionEstimationClient;
    private final TransactionOperations transactionOperations;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final MealImageStorage imageStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final RecommendationQueryService recommendationQueryService;

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

        MealAcceptedRes previousResponse = findIdempotentMeal(
                idempotencyKey,
                user,
                fingerprint,
                TEXT_MEAL_CREATE
        );
        if (previousResponse != null) {
            return previousResponse;
        }

        Meal meal = mealRepository.save(Meal.createText(user, normalizedText, eatenAtUtc));
        AiAnalysisRun analysisRun = analysisRunRepository.save(
                AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, fingerprint));
        idempotencyKeyRepository.save(IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .userId(user.getUserId())
                .operation(TEXT_MEAL_CREATE)
                .requestFingerprint(fingerprint)
                .resourceId(meal.getMealId())
                .build());

        MealAcceptedRes response = MealAcceptedRes.builder()
                .mealId(meal.getMealId())
                .status(MealStatus.ANALYZING)
                .build();
        requestRecognition(meal, analysisRun);
        return response;
    }

    @Transactional
    public MealAcceptedRes createImageMeal(String authorization, String idempotencyKey,
                                           MultipartFile image, OffsetDateTime eatenAt) {
        User user = authenticatedUser(authorization);
        requireOnboarding(user);
        ValidatedMealImage validatedImage = imageStorage.validate(image);
        String fingerprint = AccessKeyGenerator.hash(
                validatedImage.sha256() + ":" + eatenAt.toInstant()
        );
        MealAcceptedRes previousResponse = findIdempotentMeal(
                idempotencyKey,
                user,
                fingerprint,
                IMAGE_MEAL_CREATE
        );
        if (previousResponse != null) {
            return previousResponse;
        }

        String objectKey = imageStorage.store(validatedImage);
        Meal meal = mealRepository.save(Meal.createImage(
                user,
                ImageSource.GALLERY,
                objectKey,
                eatenAt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
        ));
        AiAnalysisRun analysisRun = analysisRunRepository.save(
                AiAnalysisRun.pending(meal, AnalysisRunType.INITIAL, fingerprint));
        saveIdempotencyKey(idempotencyKey, user, IMAGE_MEAL_CREATE, fingerprint, meal.getMealId());

        MealAcceptedRes response = MealAcceptedRes.builder()
                .mealId(meal.getMealId())
                .status(MealStatus.ANALYZING)
                .build();
        requestRecognition(meal, analysisRun);
        return response;
    }

    @Transactional(readOnly = true)
    public RecognitionCandidatesRes getRecognitionCandidates(String authorization, String mealId) {
        User user = authenticatedUser(authorization);
        Meal meal = activeMeal(mealId, user.getUserId());
        if (meal.getStatus() != MealStatus.REVIEW_REQUIRED) {
            throw new BaseException(ErrorResponseCode.RECOGNITION_NOT_READY);
        }
        AiAnalysisRun latestSucceededRun = analysisRunRepository
                .findFirstByMealMealIdAndStatusOrderByFinishedAtDesc(
                        mealId,
                        AnalysisStatus.SUCCEEDED
                )
                .filter(run -> run.getNormalizedResponse() != null)
                .filter(run -> run.getNormalizedResponse().getFoods() != null)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.RECOGNITION_NOT_READY));
        return RecognitionCandidatesRes.from(latestSucceededRun);
    }

    @Transactional
    public MealAcceptedRes retryRecognition(String authorization, String idempotencyKey,
                                            String mealId) {
        User user = authenticatedUser(authorization);
        Meal meal = activeMeal(mealId, user.getUserId());
        if (meal.getStatus() != MealStatus.REVIEW_REQUIRED
                && meal.getStatus() != MealStatus.FAILED) {
            throw new BaseException(ErrorResponseCode.RECOGNITION_RETRY_CONFLICT);
        }

        AiAnalysisRun latestRun = analysisRunRepository
                .findFirstByMealMealIdOrderByStartedAtDesc(mealId)
                .orElse(null);
        int attemptNo = latestRun == null ? 1 : latestRun.getAttemptNo() + 1;
        String inputFingerprint = AccessKeyGenerator.hash(
                meal.getInputType() + ":"
                        + (meal.getInputType() == MealInputType.TEXT
                        ? meal.getDirectInputText()
                        : meal.getPhotoObjectKey())
        );
        MealAcceptedRes previousResponse = findIdempotentMeal(
                idempotencyKey,
                user,
                inputFingerprint,
                RECOGNITION_RETRY
        );
        if (previousResponse != null) {
            return previousResponse;
        }

        meal.markReanalyzing();
        AiAnalysisRun analysisRun = analysisRunRepository.save(AiAnalysisRun.pending(
                meal,
                AnalysisRunType.REANALYSIS,
                inputFingerprint,
                attemptNo
        ));
        saveIdempotencyKey(idempotencyKey, user, RECOGNITION_RETRY, inputFingerprint, mealId);

        MealAcceptedRes response = MealAcceptedRes.builder()
                .mealId(mealId)
                .status(MealStatus.REANALYZING)
                .build();
        requestRecognition(meal, analysisRun);
        return response;
    }

    private void requestRecognition(Meal meal, AiAnalysisRun analysisRun) {
        eventPublisher.publishEvent(new MealRecognitionRequestedEvent(
                meal.getMealId(),
                analysisRun.getAnalysisRunId()
        ));
    }

    private MealAcceptedRes findIdempotentMeal(String idempotencyKey, User user,
                                               String requestFingerprint, String operation) {
        IdempotencyKey record = idempotencyKeyRepository.findById(idempotencyKey).orElse(null);
        if (record == null) {
            return null;
        }
        boolean sameRequest = user.getUserId().equals(record.getUserId())
                && operation.equals(record.getOperation())
                && requestFingerprint.equals(record.getRequestFingerprint())
                && record.getResourceId() != null;
        if (!sameRequest) {
            throw new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED);
        }

        Meal previousMeal = mealRepository.findById(record.getResourceId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED));
        return MealAcceptedRes.builder()
                .mealId(previousMeal.getMealId())
                .status(previousMeal.getStatus())
                .build();
    }

    private void saveIdempotencyKey(String idempotencyKey, User user, String operation,
                                    String fingerprint, String mealId) {
        idempotencyKeyRepository.save(IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .userId(user.getUserId())
                .operation(operation)
                .requestFingerprint(fingerprint)
                .resourceId(mealId)
                .build());
    }

    @Transactional(readOnly = true)
    public MealDetailRes getMeal(String authorization, String mealId) {
        User user = authenticatedUser(authorization);
        Meal meal = activeMeal(mealId, user.getUserId());
        RecognitionResult recognitionResult = analysisRunRepository
                .findFirstByMealMealIdAndStatusOrderByFinishedAtDesc(mealId, AnalysisStatus.SUCCEEDED)
                .map(AiAnalysisRun::getNormalizedResponse)
                .orElse(null);
        AiAnalysisRun latestFailedRun = meal.getStatus() == MealStatus.FAILED
                ? analysisRunRepository.findFirstByMealMealIdOrderByStartedAtDesc(mealId)
                .filter(run -> run.getStatus() == AnalysisStatus.FAILED)
                .orElse(null)
                : null;

        return MealDetailRes.builder()
                .mealId(meal.getMealId())
                .status(meal.getStatus())
                .eatenAt(toUtc(meal.getEatenAt()))
                .recognitionFailure(latestFailedRun == null
                        ? null
                        : RecognitionFailureRes.from(latestFailedRun))
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
                .recommendation(recommendationQueryService.findByMealId(mealId))
                .build();
    }

    public MealConfirmRes confirmMeal(String authorization, String mealId, MealConfirmReq request) {
        User user = authenticatedUser(authorization);
        requireMealStatus(mealId, user.getUserId(), MealStatus.REVIEW_REQUIRED,
                ErrorResponseCode.INVALID_MEAL_STATUS);

        List<PreparedMealItem> preparedItems = prepareMealItems(request.getItems());
        LocalDateTime eatenAtUtc = request.getEatenAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        MealWriteResult result = Objects.requireNonNull(transactionOperations.execute(status -> {
            Meal meal = requireMealStatusForUpdate(
                    mealId,
                    user.getUserId(),
                    MealStatus.REVIEW_REQUIRED,
                    ErrorResponseCode.INVALID_MEAL_STATUS
            );
            LocalDateTime confirmedAt = LocalDateTime.now(ZoneOffset.UTC);
            MealItemCalculation calculation = replaceMealItems(
                    mealId,
                    meal,
                    preparedItems,
                    confirmedAt
            );
            meal.updateEatenAt(eatenAtUtc);
            meal.confirm(confirmedAt);
            return new MealWriteResult(meal, calculation);
        }));

        return MealConfirmRes.builder()
                .mealId(result.meal().getMealId())
                .status(result.meal().getStatus())
                .items(result.calculation().items().stream().map(MealItemRes::from).toList())
                .nutritionSummaryStatus(summaryStatus(result.calculation().items()))
                .nutritionSummary(NutritionSummaryRes.from(result.calculation().summary()))
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

    public MealItemsUpdateRes updateMealItems(String authorization, String mealId,
                                               MealItemsUpdateReq request) {
        User user = authenticatedUser(authorization);
        requireMealStatus(mealId, user.getUserId(), MealStatus.CONFIRMED,
                ErrorResponseCode.MEAL_ITEMS_INVALID);

        List<PreparedMealItem> preparedItems = prepareMealItems(request.getItems());
        MealWriteResult result = Objects.requireNonNull(transactionOperations.execute(status -> {
            Meal meal = requireMealStatusForUpdate(
                    mealId,
                    user.getUserId(),
                    MealStatus.CONFIRMED,
                    ErrorResponseCode.MEAL_ITEMS_INVALID
            );
            return new MealWriteResult(
                    meal,
                    replaceMealItems(
                            mealId,
                            meal,
                            preparedItems,
                            LocalDateTime.now(ZoneOffset.UTC)
                    )
            );
        }));

        return MealItemsUpdateRes.builder()
                .mealId(result.meal().getMealId())
                .status(result.meal().getStatus())
                .items(result.calculation().items().stream().map(MealItemRes::from).toList())
                .nutritionSummaryStatus(summaryStatus(result.calculation().items()))
                .nutritionSummary(NutritionSummaryRes.from(result.calculation().summary()))
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

    private Meal requireMealStatus(String mealId, String userId, MealStatus requiredStatus,
                                   ErrorResponseCode errorCode) {
        Meal meal = activeMeal(mealId, userId);
        validateMealStatus(meal, requiredStatus, errorCode);
        return meal;
    }

    private Meal requireMealStatusForUpdate(String mealId, String userId,
                                            MealStatus requiredStatus,
                                            ErrorResponseCode errorCode) {
        Meal meal = mealRepository.findOwnedByIdForUpdate(mealId, userId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_NOT_FOUND));
        validateMealStatus(meal, requiredStatus, errorCode);
        return meal;
    }

    private void validateMealStatus(Meal meal, MealStatus requiredStatus,
                                    ErrorResponseCode errorCode) {
        if (meal.getStatus() != requiredStatus) {
            throw new BaseException(errorCode);
        }
    }

    private List<PreparedMealItem> prepareMealItems(List<MealItemInputReq> requests) {
        return requests.stream()
                .map(this::prepareMealItem)
                .toList();
    }

    private List<MealItem> createConfirmedItems(Meal meal, List<PreparedMealItem> preparedItems) {
        return java.util.stream.IntStream.range(0, preparedItems.size())
                .mapToObj(index -> createConfirmedItem(meal, preparedItems.get(index), index))
                .toList();
    }

    private MealItemCalculation replaceMealItems(String mealId, Meal meal,
                                                  List<PreparedMealItem> preparedItems,
                                                  LocalDateTime calculatedAt) {
        List<MealItem> items = createConfirmedItems(meal, preparedItems);
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
                        .basis(summaryBasis(items))
                        .calculatedAt(calculatedAt)
                        .build()
        );

        return new MealItemCalculation(savedItems, summary);
    }

    private PreparedMealItem prepareMealItem(MealItemInputReq request) {
        String requestedFoodId = trimToNull(request.getFoodId());
        FoodCatalogMatch catalogMatch = resolveCatalogMatch(requestedFoodId, request.getFoodName());
        NutritionSnapshot nutritionSnapshot = resolveNutrition(catalogMatch, request);

        return new PreparedMealItem(
                catalogMatch == null ? null : catalogMatch.food().getFoodId(),
                request.getFoodName().trim(),
                request.getAmount(),
                request.getUnit().trim(),
                requestedFoodId == null ? MealItemSource.USER_ADDED : MealItemSource.USER_EDITED,
                nutritionSnapshot
        );
    }

    private MealItem createConfirmedItem(Meal meal, PreparedMealItem preparedItem, int sortOrder) {
        NutritionSnapshot nutritionSnapshot = preparedItem.nutritionSnapshot();
        return MealItem.builder()
                .mealItemId(UUID.randomUUID().toString())
                .meal(meal)
                .foodId(preparedItem.foodId())
                .foodName(preparedItem.foodName())
                .amount(preparedItem.amount())
                .amountUnit(preparedItem.amountUnit())
                .source(preparedItem.source())
                .sortOrder(sortOrder)
                .nutrition(nutritionSnapshot.nutrition())
                .nutritionBasis(nutritionSnapshot.basis())
                .nutritionProvider(nutritionSnapshot.provider())
                .nutritionModel(nutritionSnapshot.model())
                .nutritionPromptVersion(nutritionSnapshot.promptVersion())
                .nutritionConfidence(nutritionSnapshot.confidence())
                .build();
    }

    private FoodCatalogMatch resolveCatalogMatch(String requestedFoodId, String foodName) {
        if (requestedFoodId != null) {
            return foodMatchingService.findById(requestedFoodId)
                    .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_ITEMS_INVALID));
        }
        return foodMatchingService.matchByName(foodName).orElse(null);
    }

    private NutritionSnapshot resolveNutrition(FoodCatalogMatch catalogMatch,
                                                MealItemInputReq request) {
        if (catalogMatch != null) {
            NutritionValues catalogNutrition = calculateNutrition(catalogMatch, request);
            return catalogNutrition == null
                    ? NutritionSnapshot.unknown()
                    : NutritionSnapshot.catalog(catalogNutrition);
        }

        try {
            return nutritionEstimationClient.estimate(new FoodNutritionEstimationInput(
                            request.getFoodName().trim(),
                            request.getAmount(),
                            request.getUnit().trim()
                    ))
                    .filter(this::validEstimate)
                    .map(NutritionSnapshot::estimated)
                    .orElseGet(NutritionSnapshot::unknown);
        } catch (RuntimeException exception) {
            log.warn(
                    "Food nutrition estimation failed; preserving UNKNOWN nutrition - type: {}",
                    exception.getClass().getSimpleName()
            );
            return NutritionSnapshot.unknown();
        }
    }

    private boolean validEstimate(FoodNutritionEstimationResponse estimate) {
        return estimate != null
                && estimate.nutrition() != null
                && estimate.confidence() != null
                && estimate.confidence().compareTo(BigDecimal.ZERO) >= 0
                && estimate.confidence().compareTo(BigDecimal.ONE) <= 0
                && !isBlank(estimate.provider())
                && !isBlank(estimate.model())
                && !isBlank(estimate.promptVersion());
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

    private NutritionBasis summaryBasis(List<MealItem> items) {
        if (items.stream().anyMatch(item -> item.getNutritionBasis() == NutritionBasis.AI_ESTIMATE)) {
            return NutritionBasis.AI_ESTIMATE;
        }
        if (items.stream().anyMatch(item -> item.getNutritionBasis() == NutritionBasis.CATALOG)) {
            return NutritionBasis.CATALOG;
        }
        return NutritionBasis.USER_CONFIRMED;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private record MealItemCalculation(
            List<MealItem> items,
            MealNutritionSummary summary
    ) {
    }

    private record MealWriteResult(
            Meal meal,
            MealItemCalculation calculation
    ) {
    }

    private record PreparedMealItem(
            String foodId,
            String foodName,
            BigDecimal amount,
            String amountUnit,
            MealItemSource source,
            NutritionSnapshot nutritionSnapshot
    ) {
    }

    private record NutritionSnapshot(
            NutritionValues nutrition,
            NutritionBasis basis,
            String provider,
            String model,
            String promptVersion,
            BigDecimal confidence
    ) {
        private static NutritionSnapshot catalog(NutritionValues nutrition) {
            return new NutritionSnapshot(
                    nutrition,
                    NutritionBasis.CATALOG,
                    null,
                    null,
                    null,
                    null
            );
        }

        private static NutritionSnapshot estimated(FoodNutritionEstimationResponse estimate) {
            return new NutritionSnapshot(
                    estimate.nutrition(),
                    NutritionBasis.AI_ESTIMATE,
                    estimate.provider(),
                    estimate.model(),
                    estimate.promptVersion(),
                    estimate.confidence()
            );
        }

        private static NutritionSnapshot unknown() {
            return new NutritionSnapshot(null, null, null, null, null, null);
        }
    }
}
