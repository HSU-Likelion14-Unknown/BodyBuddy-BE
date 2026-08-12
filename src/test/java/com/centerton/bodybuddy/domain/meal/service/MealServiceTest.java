package com.centerton.bodybuddy.domain.meal.service;

import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.AnalysisStatus;
import com.centerton.bodybuddy.domain.analysis.repository.AiAnalysisRunRepository;
import com.centerton.bodybuddy.domain.meal.dto.MealAcceptedRes;
import com.centerton.bodybuddy.domain.meal.dto.TextMealCreateReq;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private MealRepository mealRepository;
    @Mock
    private MealItemRepository mealItemRepository;
    @Mock
    private MealNutritionSummaryRepository nutritionSummaryRepository;
    @Mock
    private AiAnalysisRunRepository analysisRunRepository;

    private MealService mealService;
    private User user;

    @BeforeEach
    void setUp() {
        mealService = new MealService(
                userRepository,
                mealRepository,
                mealItemRepository,
                nutritionSummaryRepository,
                analysisRunRepository
        );
        user = User.builder()
                .userId("user-id")
                .accessKeyHash("access-key-hash")
                .onboardingCompletedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createsTextMealAndPendingAnalysis() {
        when(userRepository.findByAccessKeyHash(anyString())).thenReturn(Optional.of(user));
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
        verify(mealRepository).save(mealCaptor.capture());
        assertThat(mealCaptor.getValue().getDirectInputText()).isEqualTo("참치김밥 한 줄");
        assertThat(mealCaptor.getValue().getEatenAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 13, 3, 30));
        assertThat(response.getStatus()).isEqualTo(MealStatus.ANALYZING);
        assertThat(response.getAnalysis().getStatus()).isEqualTo(AnalysisStatus.PENDING);
    }

    @Test
    void rejectsMealCreationBeforeOnboarding() {
        User pendingUser = User.builder()
                .userId("pending-user")
                .accessKeyHash("access-key-hash")
                .build();
        when(userRepository.findByAccessKeyHash(anyString())).thenReturn(Optional.of(pendingUser));

        TextMealCreateReq request = new TextMealCreateReq(
                "샐러드",
                OffsetDateTime.now()
        );

        assertThatThrownBy(() -> mealService.createTextMeal(
                "Bearer raw-access-key",
                "idempotency-key",
                request
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.ONBOARDING_NOT_COMPLETED));
    }

    @Test
    void rejectsConfirmWhenAnalysisIsNotReady() {
        Meal meal = Meal.createText(user, "샐러드", LocalDateTime.now());
        ReflectionTestUtils.setField(meal, "version", 0L);
        when(userRepository.findByAccessKeyHash(anyString())).thenReturn(Optional.of(user));
        when(mealRepository.findByMealIdAndUserUserIdAndDeletedAtIsNull("meal-id", "user-id"))
                .thenReturn(Optional.of(meal));

        assertThatThrownBy(() -> mealService.confirmMeal(
                "Bearer raw-access-key",
                "meal-id",
                0L
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.INVALID_MEAL_STATUS));
    }
}
