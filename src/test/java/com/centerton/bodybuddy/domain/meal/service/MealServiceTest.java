package com.centerton.bodybuddy.domain.meal.service;

import com.centerton.bodybuddy.domain.analysis.client.FoodNutritionEstimationClient;
import com.centerton.bodybuddy.domain.analysis.client.FoodNutritionEstimationInput;
import com.centerton.bodybuddy.domain.analysis.client.FoodNutritionEstimationResponse;
import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisRunType;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisStatus;
import com.centerton.bodybuddy.domain.analysis.entity.RecognitionResult;
import com.centerton.bodybuddy.domain.analysis.entity.RecognitionFailureReason;
import com.centerton.bodybuddy.domain.analysis.entity.RecognizedFood;
import com.centerton.bodybuddy.domain.analysis.repository.AiAnalysisRunRepository;
import com.centerton.bodybuddy.domain.analysis.service.MealRecognitionRequestedEvent;
import com.centerton.bodybuddy.domain.auth.entity.IdempotencyKey;
import com.centerton.bodybuddy.domain.auth.repository.IdempotencyKeyRepository;
import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.entity.FoodNutrition;
import com.centerton.bodybuddy.domain.food.service.FoodCatalogMatch;
import com.centerton.bodybuddy.domain.food.service.FoodMatchingService;
import com.centerton.bodybuddy.domain.meal.dto.*;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.MealNutritionSummary;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import com.centerton.bodybuddy.domain.meal.entity.NutritionBasis;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.meal.repository.MealItemRepository;
import com.centerton.bodybuddy.domain.meal.repository.MealNutritionSummaryRepository;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
import com.centerton.bodybuddy.domain.meal.storage.MealImageStorage;
import com.centerton.bodybuddy.domain.meal.storage.ValidatedMealImage;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationRes;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationStatus;
import com.centerton.bodybuddy.domain.recommendation.service.RecommendationQueryService;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MealRepository mealRepository;
    @Mock private MealItemRepository mealItemRepository;
    @Mock private MealNutritionSummaryRepository nutritionSummaryRepository;
    @Mock private AiAnalysisRunRepository analysisRunRepository;
    @Mock private FoodMatchingService foodMatchingService;
    @Mock private FoodNutritionEstimationClient nutritionEstimationClient;
    @Mock private TransactionOperations transactionOperations;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private MealImageStorage imageStorage;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RecommendationQueryService recommendationQueryService;

    private MealService mealService;
    private User user;

    @BeforeEach
    void setUp() {
        mealService = new MealService(
                userRepository,
                mealRepository,
                mealItemRepository,
                nutritionSummaryRepository,
                analysisRunRepository,
                foodMatchingService,
                nutritionEstimationClient,
                transactionOperations,
                idempotencyKeyRepository,
                imageStorage,
                eventPublisher,
                recommendationQueryService
        );
        lenient().when(transactionOperations.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        lenient().when(mealRepository.findOwnedByIdForUpdate(anyString(), anyString()))
                .thenAnswer(invocation -> mealRepository.findByMealIdAndUserUserId(
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        user = User.builder()
                .userId("user-id")
                .accessKeyHash("access-key-hash")
                .onboardingCompletedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createsTextMealAndPendingAnalysis() {
        authenticate(user);
        when(idempotencyKeyRepository.findById("idempotency-key")).thenReturn(Optional.empty());
        when(mealRepository.save(any(Meal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisRunRepository.save(any(AiAnalysisRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TextMealCreateReq request = new TextMealCreateReq(
                "  참치김밥 한 줄  ",
                OffsetDateTime.of(2026, 8, 13, 12, 30, 0, 0, ZoneOffset.ofHours(9))
        );

        MealAcceptedRes response = mealService.createTextMeal(
                "Bearer raw-access-key",
                "idempotency-key",
                request
        );

        ArgumentCaptor<Meal> mealCaptor = ArgumentCaptor.forClass(Meal.class);
        ArgumentCaptor<AiAnalysisRun> analysisCaptor = ArgumentCaptor.forClass(AiAnalysisRun.class);
        verify(mealRepository).save(mealCaptor.capture());
        verify(analysisRunRepository).save(analysisCaptor.capture());
        assertThat(mealCaptor.getValue().getDirectInputText()).isEqualTo("참치김밥 한 줄");
        assertThat(mealCaptor.getValue().getEatenAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 13, 3, 30));
        assertThat(analysisCaptor.getValue().getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(response.getStatus()).isEqualTo(MealStatus.ANALYZING);
    }

    @Test
    void returnsPreviousMealForSameIdempotentRequest() {
        authenticate(user);
        TextMealCreateReq request = new TextMealCreateReq(
                "참치김밥 한 줄",
                OffsetDateTime.of(2026, 8, 13, 3, 30, 0, 0, ZoneOffset.UTC)
        );
        String fingerprint = com.centerton.bodybuddy.domain.auth.util.AccessKeyGenerator.hash(
                request.getText() + ":" + request.getEatenAt().toInstant()
        );
        Meal previousMeal = Meal.createText(user, request.getText(), request.getEatenAt().toLocalDateTime());
        IdempotencyKey record = IdempotencyKey.builder()
                .idempotencyKey("same-key")
                .userId(user.getUserId())
                .operation("TEXT_MEAL_CREATE")
                .requestFingerprint(fingerprint)
                .resourceId(previousMeal.getMealId())
                .build();
        when(idempotencyKeyRepository.findById("same-key")).thenReturn(Optional.of(record));
        when(mealRepository.findById(previousMeal.getMealId())).thenReturn(Optional.of(previousMeal));

        MealAcceptedRes response = mealService.createTextMeal(
                "Bearer raw-access-key",
                "same-key",
                request
        );

        assertThat(response.getMealId()).isEqualTo(previousMeal.getMealId());
        verify(mealRepository, never()).save(any());
    }

    @Test
    void rejectsMealCreationBeforeOnboarding() {
        User pendingUser = User.builder()
                .userId("pending-user")
                .accessKeyHash("access-key-hash")
                .build();
        authenticate(pendingUser);

        TextMealCreateReq request = new TextMealCreateReq("샐러드", OffsetDateTime.now());

        assertThatThrownBy(() -> mealService.createTextMeal(
                "Bearer raw-access-key",
                "idempotency-key",
                request
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                                .isEqualTo(ErrorResponseCode.ONBOARDING_NOT_COMPLETED));
    }

    @Test
    void createsImageMealAndStartsCommonRecognitionFlow() {
        authenticate(user);
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "meal.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        ValidatedMealImage validated = new ValidatedMealImage(
                new byte[]{1, 2, 3},
                "image/png",
                "png",
                "image-sha256"
        );
        when(imageStorage.validate(image)).thenReturn(validated);
        when(imageStorage.store(validated)).thenReturn("2026/08/meal.png");
        when(idempotencyKeyRepository.findById("image-key")).thenReturn(Optional.empty());
        when(mealRepository.save(any(Meal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisRunRepository.save(any(AiAnalysisRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MealAcceptedRes response = mealService.createImageMeal(
                "Bearer raw-access-key",
                "image-key",
                image,
                OffsetDateTime.of(2026, 8, 13, 12, 30, 0, 0, ZoneOffset.ofHours(9))
        );

        ArgumentCaptor<Meal> mealCaptor = ArgumentCaptor.forClass(Meal.class);
        ArgumentCaptor<AiAnalysisRun> runCaptor = ArgumentCaptor.forClass(AiAnalysisRun.class);
        verify(mealRepository).save(mealCaptor.capture());
        verify(analysisRunRepository).save(runCaptor.capture());
        assertThat(mealCaptor.getValue().getInputType())
                .isEqualTo(com.centerton.bodybuddy.domain.meal.entity.MealInputType.IMAGE);
        assertThat(mealCaptor.getValue().getPhotoObjectKey()).isEqualTo("2026/08/meal.png");
        assertThat(mealCaptor.getValue().getEatenAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 13, 3, 30));
        assertThat(response.getStatus()).isEqualTo(MealStatus.ANALYZING);
        verify(eventPublisher).publishEvent(any(MealRecognitionRequestedEvent.class));
    }

    @Test
    void returnsLatestSuccessfulRecognitionCandidatesWithConfidence() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        meal.markReviewRequired();
        AiAnalysisRun run = AiAnalysisRun.pending(
                meal,
                AnalysisRunType.REANALYSIS,
                "fingerprint",
                2
        );
        run.markRunning();
        run.succeed(
                RecognitionResult.builder()
                        .foods(List.of(RecognizedFood.builder()
                                .foodId("food-id")
                                .foodName("두부")
                                .confidence(new BigDecimal("0.9100"))
                                .build()))
                        .build(),
                "FAKE",
                "fake-v1",
                "prompt-v1",
                "response-id",
                10,
                null,
                null
        );
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));
        when(analysisRunRepository.findFirstByMealMealIdAndStatusOrderByFinishedAtDesc(
                "meal-id",
                AnalysisStatus.SUCCEEDED
        )).thenReturn(Optional.of(run));

        RecognitionCandidatesRes response = mealService.getRecognitionCandidates(
                "Bearer raw-access-key",
                "meal-id"
        );

        assertThat(response.getMealId()).isEqualTo(meal.getMealId());
        assertThat(response.getCandidates()).hasSize(1);
        assertThat(response.getCandidates().getFirst().getAiFoodName()).isEqualTo("두부");
        assertThat(response.getCandidates().getFirst().getFoodId()).isEqualTo("food-id");
        assertThat(response.getCandidates().getFirst().getConfidence())
                .isEqualByComparingTo("0.9100");
    }

    @Test
    void doesNotReturnPreviousCandidatesWhileRetryIsRunning() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        meal.markReviewRequired();
        meal.markReanalyzing();
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));

        assertThatThrownBy(() -> mealService.getRecognitionCandidates(
                "Bearer raw-access-key",
                "meal-id"
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.RECOGNITION_NOT_READY));
    }

    @Test
    void createsNewRunAndMarksMealReanalyzingOnRetry() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        meal.markReviewRequired();
        AiAnalysisRun previousRun = AiAnalysisRun.pending(
                meal,
                AnalysisRunType.INITIAL,
                "fingerprint",
                1
        );
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));
        when(analysisRunRepository.findFirstByMealMealIdOrderByStartedAtDesc("meal-id"))
                .thenReturn(Optional.of(previousRun));
        when(idempotencyKeyRepository.findById("retry-key")).thenReturn(Optional.empty());
        when(analysisRunRepository.save(any(AiAnalysisRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MealAcceptedRes response = mealService.retryRecognition(
                "Bearer raw-access-key",
                "retry-key",
                "meal-id"
        );

        ArgumentCaptor<AiAnalysisRun> runCaptor = ArgumentCaptor.forClass(AiAnalysisRun.class);
        verify(analysisRunRepository).save(runCaptor.capture());
        assertThat(response.getStatus()).isEqualTo(MealStatus.REANALYZING);
        assertThat(meal.getStatus()).isEqualTo(MealStatus.REANALYZING);
        assertThat(runCaptor.getValue().getRunType()).isEqualTo(AnalysisRunType.REANALYSIS);
        assertThat(runCaptor.getValue().getAttemptNo()).isEqualTo(2);
        verify(eventPublisher).publishEvent(any(MealRecognitionRequestedEvent.class));
    }

    @Test
    void confirmsItemsAndCalculatesNutritionByConsumedAmount() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        meal.markReviewRequired();
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));
        when(foodMatchingService.findById("food-id"))
                .thenReturn(Optional.of(foodCatalogMatch()));
        when(mealItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(nutritionSummaryRepository.findById("meal-id")).thenReturn(Optional.empty());
        when(nutritionSummaryRepository.save(any(MealNutritionSummary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MealConfirmReq request = new MealConfirmReq(
                List.of(new MealItemInputReq(
                        "food-id", "두부", new BigDecimal("50"), "g"
                )),
                OffsetDateTime.of(2026, 8, 13, 12, 0, 0, 0, ZoneOffset.ofHours(9))
        );

        MealConfirmRes response = mealService.confirmMeal(
                "Bearer raw-access-key",
                "meal-id",
                request
        );

        assertThat(response.getStatus()).isEqualTo(MealStatus.CONFIRMED);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getCaloriesKcal())
                .isEqualByComparingTo("100.00");
        assertThat(response.getItems().getFirst().getCalciumMg())
                .isEqualByComparingTo("50.00");
        assertThat(response.getItems().getFirst().getNutritionStatus())
                .isEqualTo(NutritionStatus.CALCULATED);
        assertThat(response.getItems().getFirst().getNutritionBasis())
                .isEqualTo(NutritionBasis.CATALOG);
        assertThat(response.getNutritionSummaryStatus())
                .isEqualTo(NutritionSummaryStatus.COMPLETE);
        assertThat(response.getNutritionSummary().getProteinG())
                .isEqualByComparingTo("5.00");
        assertThat(response.getNutritionSummary().getBasis())
                .isEqualTo(NutritionBasis.CATALOG);
        verify(nutritionEstimationClient, never()).estimate(any());
    }

    @Test
    void completesConfirmedMeal() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        meal.markReviewRequired();
        meal.confirm(LocalDateTime.now());
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));

        MealCompleteRes response = mealService.completeMeal(
                "Bearer raw-access-key",
                "meal-id"
        );

        assertThat(response.getStatus()).isEqualTo(MealStatus.COMPLETED);
        assertThat(response.getCompletedAt()).isNotNull();
        assertThat(meal.getCompletedAt()).isNotNull();
    }

    @Test
    void rejectsCompletionUnlessMealIsConfirmed() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));

        assertThatThrownBy(() -> mealService.completeMeal(
                "Bearer raw-access-key",
                "meal-id"
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.MEAL_COMPLETION_CONFLICT));
    }

    @Test
    void updatesConfirmedItemsAndRecalculatesNutrition() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        meal.markReviewRequired();
        meal.confirm(LocalDateTime.now());
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));
        when(foodMatchingService.findById("food-id"))
                .thenReturn(Optional.of(foodCatalogMatch()));
        when(mealItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(nutritionSummaryRepository.findById("meal-id")).thenReturn(Optional.empty());
        when(nutritionSummaryRepository.save(any(MealNutritionSummary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MealItemsUpdateReq request = new MealItemsUpdateReq(
                List.of(new MealItemInputReq(
                        "food-id", "두부", new BigDecimal("50"), "g"
                ))
        );

        MealItemsUpdateRes response = mealService.updateMealItems(
                "Bearer raw-access-key",
                "meal-id",
                request
        );

        assertThat(response.getStatus()).isEqualTo(MealStatus.CONFIRMED);
        assertThat(response.getNutritionSummary().getCaloriesKcal())
                .isEqualByComparingTo("100.00");
        assertThat(response.getNutritionSummary().getProteinG())
                .isEqualByComparingTo("5.00");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getNutritionSummaryStatus())
                .isEqualTo(NutritionSummaryStatus.COMPLETE);
        verify(mealItemRepository).deleteAllByMealMealId("meal-id");
        verify(mealItemRepository).flush();
        verify(nutritionSummaryRepository).flush();
    }

    @Test
    void rejectsItemUpdateUnlessMealIsConfirmed() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));
        MealItemsUpdateReq request = new MealItemsUpdateReq(
                List.of(new MealItemInputReq(
                        "food-id", "두부", new BigDecimal("50"), "g"
                ))
        );

        assertThatThrownBy(() -> mealService.updateMealItems(
                "Bearer raw-access-key",
                "meal-id",
                request
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.MEAL_ITEMS_INVALID));
        verify(foodMatchingService, never()).findById(anyString());
    }

    @Test
    void matchesFoodNameWhenDirectInputHasNoFoodId() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        meal.markReviewRequired();
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));
        when(foodMatchingService.matchByName("두부"))
                .thenReturn(Optional.of(foodCatalogMatch()));
        when(mealItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(nutritionSummaryRepository.findById("meal-id")).thenReturn(Optional.empty());
        when(nutritionSummaryRepository.save(any(MealNutritionSummary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MealConfirmRes response = mealService.confirmMeal(
                "Bearer raw-access-key",
                "meal-id",
                new MealConfirmReq(
                        List.of(new MealItemInputReq(
                                null, "두부", new BigDecimal("50"), "g"
                        )),
                        OffsetDateTime.now()
                )
        );

        assertThat(response.getItems().getFirst().getFoodId()).isEqualTo("food-id");
        assertThat(response.getItems().getFirst().getSource())
                .isEqualTo(com.centerton.bodybuddy.domain.meal.entity.MealItemSource.USER_ADDED);
        assertThat(response.getItems().getFirst().getNutritionStatus())
                .isEqualTo(NutritionStatus.CALCULATED);
    }

    @Test
    void storesUnmatchedDirectInputWithPartialNutritionSummary() {
        authenticate(user);
        Meal meal = Meal.createText(user, "엄마표 특별식", LocalDateTime.now());
        meal.markReviewRequired();
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));
        when(foodMatchingService.matchByName("엄마표 특별식")).thenReturn(Optional.empty());
        when(nutritionEstimationClient.estimate(any(FoodNutritionEstimationInput.class)))
                .thenReturn(Optional.empty());
        when(mealItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(nutritionSummaryRepository.findById("meal-id")).thenReturn(Optional.empty());
        when(nutritionSummaryRepository.save(any(MealNutritionSummary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MealConfirmRes response = mealService.confirmMeal(
                "Bearer raw-access-key",
                "meal-id",
                new MealConfirmReq(
                        List.of(new MealItemInputReq(
                                null, "엄마표 특별식", new BigDecimal("1"), "그릇"
                        )),
                        OffsetDateTime.now()
                )
        );

        assertThat(response.getItems().getFirst().getFoodId()).isNull();
        assertThat(response.getItems().getFirst().getNutritionStatus())
                .isEqualTo(NutritionStatus.UNKNOWN);
        assertThat(response.getNutritionSummaryStatus())
                .isEqualTo(NutritionSummaryStatus.PARTIAL);
        assertThat(response.getNutritionSummary().getCaloriesKcal()).isNull();
    }

    @Test
    void estimatesUnmatchedFoodForConfirmedAmountAndStoresMetadata() {
        authenticate(user);
        Meal meal = Meal.createText(user, "짜장면", LocalDateTime.now());
        meal.markReviewRequired();
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));
        when(foodMatchingService.matchByName("짜장면")).thenReturn(Optional.empty());
        when(nutritionEstimationClient.estimate(any(FoodNutritionEstimationInput.class)))
                .thenReturn(Optional.of(new FoodNutritionEstimationResponse(
                        NutritionValues.builder()
                                .caloriesKcal(new BigDecimal("650"))
                                .carbohydrateG(new BigDecimal("110"))
                                .proteinG(new BigDecimal("20"))
                                .fatG(new BigDecimal("15"))
                                .fiberG(new BigDecimal("6"))
                                .sodiumMg(new BigDecimal("1800"))
                                .calciumMg(new BigDecimal("80"))
                                .ironMg(new BigDecimal("3"))
                                .potassiumMg(new BigDecimal("550"))
                                .vitaminAMcgRae(new BigDecimal("120"))
                                .vitaminCMg(new BigDecimal("12"))
                                .build(),
                        new BigDecimal("0.78"),
                        "OPENAI",
                        "gpt-5-mini-2025-08-07",
                        "food-nutrition-estimation-v1",
                        "resp_nutrition",
                        80,
                        30
                )));
        when(mealItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(nutritionSummaryRepository.findById("meal-id")).thenReturn(Optional.empty());
        when(nutritionSummaryRepository.save(any(MealNutritionSummary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MealConfirmRes response = mealService.confirmMeal(
                "Bearer raw-access-key",
                "meal-id",
                new MealConfirmReq(
                        List.of(new MealItemInputReq(
                                null, "짜장면", new BigDecimal("1.5"), "그릇"
                        )),
                        OffsetDateTime.now()
                )
        );

        MealItemRes item = response.getItems().getFirst();
        assertThat(item.getFoodId()).isNull();
        assertThat(item.getNutritionStatus()).isEqualTo(NutritionStatus.ESTIMATED);
        assertThat(item.getNutritionBasis()).isEqualTo(NutritionBasis.AI_ESTIMATE);
        assertThat(item.getNutritionProvider()).isEqualTo("OPENAI");
        assertThat(item.getNutritionModel()).isEqualTo("gpt-5-mini-2025-08-07");
        assertThat(item.getNutritionPromptVersion()).isEqualTo("food-nutrition-estimation-v1");
        assertThat(item.getNutritionConfidence()).isEqualByComparingTo("0.78");
        assertThat(item.getCaloriesKcal()).isEqualByComparingTo("650");
        assertThat(response.getNutritionSummaryStatus()).isEqualTo(NutritionSummaryStatus.COMPLETE);
        assertThat(response.getNutritionSummary().getCaloriesKcal()).isEqualByComparingTo("650");
        assertThat(response.getNutritionSummary().getBasis())
                .isEqualTo(NutritionBasis.AI_ESTIMATE);

        ArgumentCaptor<FoodNutritionEstimationInput> inputCaptor =
                ArgumentCaptor.forClass(FoodNutritionEstimationInput.class);
        verify(nutritionEstimationClient).estimate(inputCaptor.capture());
        assertThat(inputCaptor.getValue().foodName()).isEqualTo("짜장면");
        assertThat(inputCaptor.getValue().consumedAmount()).isEqualByComparingTo("1.5");
        assertThat(inputCaptor.getValue().consumedUnit()).isEqualTo("그릇");
        var order = inOrder(nutritionEstimationClient, transactionOperations);
        order.verify(nutritionEstimationClient).estimate(any(FoodNutritionEstimationInput.class));
        order.verify(transactionOperations).execute(any());
        verify(mealRepository).findOwnedByIdForUpdate("meal-id", "user-id");
    }

    @Test
    void permanentlyDeletesMealAndCurrentChildren() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));
        when(nutritionSummaryRepository.findById("meal-id")).thenReturn(Optional.empty());

        mealService.deleteMeal("Bearer raw-access-key", "meal-id");

        verify(analysisRunRepository).deleteAllByMealMealId("meal-id");
        verify(mealItemRepository).deleteAllByMealMealId("meal-id");
        verify(mealRepository).delete(meal);
    }

    @Test
    void includesPersistedRecommendationInMealDetail() {
        authenticate(user);
        Meal meal = Meal.createText(user, "식사", LocalDateTime.now());
        RecommendationRes recommendation = RecommendationRes.builder()
                .recommendationId("recommendation-id")
                .status(RecommendationStatus.CREATED)
                .build();
        when(mealRepository.findByMealIdAndUserUserId(meal.getMealId(), "user-id"))
                .thenReturn(Optional.of(meal));
        when(analysisRunRepository.findFirstByMealMealIdAndStatusOrderByFinishedAtDesc(
                meal.getMealId(), AnalysisStatus.SUCCEEDED)).thenReturn(Optional.empty());
        when(mealItemRepository.findAllByMealMealIdOrderBySortOrderAsc(meal.getMealId()))
                .thenReturn(List.of());
        when(nutritionSummaryRepository.findById(meal.getMealId()))
                .thenReturn(Optional.empty());
        when(recommendationQueryService.findByMealId(meal.getMealId()))
                .thenReturn(recommendation);

        MealDetailRes result = mealService.getMeal(
                "Bearer raw-access-key",
                meal.getMealId()
        );

        assertThat(result.getRecommendation()).isSameAs(recommendation);
    }

    @Test
    void includesFriendlyRecognitionFailureInFailedMealDetail() {
        authenticate(user);
        Meal meal = Meal.createText(user, "모호한 음식", LocalDateTime.now());
        meal.markFailed();
        AiAnalysisRun run = AiAnalysisRun.pending(
                meal,
                AnalysisRunType.INITIAL,
                "fingerprint"
        );
        run.markRunning();
        run.fail(
                RecognitionFailureReason.LOW_CONFIDENCE.getErrorCode(),
                "provider detail that must not be exposed",
                10
        );
        when(mealRepository.findByMealIdAndUserUserId(meal.getMealId(), "user-id"))
                .thenReturn(Optional.of(meal));
        when(analysisRunRepository.findFirstByMealMealIdAndStatusOrderByFinishedAtDesc(
                meal.getMealId(), AnalysisStatus.SUCCEEDED)).thenReturn(Optional.empty());
        when(analysisRunRepository.findFirstByMealMealIdAndStatusOrderByFinishedAtDesc(
                meal.getMealId(), AnalysisStatus.FAILED)).thenReturn(Optional.of(run));
        when(mealItemRepository.findAllByMealMealIdOrderBySortOrderAsc(meal.getMealId()))
                .thenReturn(List.of());
        when(nutritionSummaryRepository.findById(meal.getMealId()))
                .thenReturn(Optional.empty());

        MealDetailRes result = mealService.getMeal(
                "Bearer raw-access-key",
                meal.getMealId()
        );

        assertThat(result.getRecognitionFailure().getReason())
                .isEqualTo(RecognitionFailureReason.LOW_CONFIDENCE);
        assertThat(result.getRecognitionFailure().getMessage())
                .isEqualTo(RecognitionFailureReason.LOW_CONFIDENCE.getMessage())
                .doesNotContain("provider detail");
        verify(analysisRunRepository)
                .findFirstByMealMealIdAndStatusOrderByFinishedAtDesc(
                        meal.getMealId(),
                        AnalysisStatus.FAILED
                );
        verify(analysisRunRepository, never())
                .findFirstByMealMealIdOrderByStartedAtDesc(meal.getMealId());
    }

    private void authenticate(User authenticatedUser) {
        when(userRepository.findByAccessKeyHash(anyString()))
                .thenReturn(Optional.of(authenticatedUser));
    }

    private FoodNutrition foodNutrition() {
        Food food = Food.builder()
                .foodId("food-id")
                .canonicalName("두부")
                .normalizedName("두부")
                .active(true)
                .build();
        return FoodNutrition.builder()
                .food(food)
                .referenceAmount(new BigDecimal("100"))
                .referenceUnit("g")
                .nutrition(NutritionValues.builder()
                        .caloriesKcal(new BigDecimal("200"))
                        .proteinG(new BigDecimal("10"))
                        .calciumMg(new BigDecimal("100"))
                        .build())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private FoodCatalogMatch foodCatalogMatch() {
        FoodNutrition nutrition = foodNutrition();
        return new FoodCatalogMatch(nutrition.getFood(), nutrition);
    }
}
