package com.centerton.bodybuddy.domain.meal.service;

import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisStatus;
import com.centerton.bodybuddy.domain.analysis.repository.AiAnalysisRunRepository;
import com.centerton.bodybuddy.domain.auth.entity.IdempotencyKey;
import com.centerton.bodybuddy.domain.auth.repository.IdempotencyKeyRepository;
import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.entity.FoodNutrition;
import com.centerton.bodybuddy.domain.food.repository.FoodNutritionRepository;
import com.centerton.bodybuddy.domain.meal.dto.*;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.MealNutritionSummary;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.meal.repository.MealItemRepository;
import com.centerton.bodybuddy.domain.meal.repository.MealNutritionSummaryRepository;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MealRepository mealRepository;
    @Mock private MealItemRepository mealItemRepository;
    @Mock private MealNutritionSummaryRepository nutritionSummaryRepository;
    @Mock private AiAnalysisRunRepository analysisRunRepository;
    @Mock private FoodNutritionRepository foodNutritionRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;

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
                foodNutritionRepository,
                idempotencyKeyRepository
        );
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
    void confirmsItemsAndCalculatesNutritionByConsumedAmount() {
        authenticate(user);
        Meal meal = Meal.createText(user, "두부", LocalDateTime.now());
        meal.markReviewRequired();
        when(mealRepository.findByMealIdAndUserUserId("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));
        when(foodNutritionRepository.findById("food-id"))
                .thenReturn(Optional.of(foodNutrition()));
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
        assertThat(response.getNutritionSummary().getProteinG())
                .isEqualByComparingTo("5.00");
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
                        .build())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
