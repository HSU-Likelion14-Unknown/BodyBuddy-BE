package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.auth.entity.IdempotencyKey;
import com.centerton.bodybuddy.domain.auth.repository.IdempotencyKeyRepository;
import com.centerton.bodybuddy.domain.auth.util.AccessKeyGenerator;
import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.repository.FoodRepository;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationDecisionReq;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationDecisionRes;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationRes;
import com.centerton.bodybuddy.domain.recommendation.config.RecommendationPolicyProperties;
import com.centerton.bodybuddy.domain.recommendation.entity.NoRecommendationReason;
import com.centerton.bodybuddy.domain.recommendation.entity.Recommendation;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecision;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecisionType;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationIngredient;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationStatus;
import com.centerton.bodybuddy.domain.recommendation.model.IngredientDishRecommendation;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutrientGap;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.RankedIngredient;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationCreationResult;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendationPlan;
import com.centerton.bodybuddy.domain.recommendation.model.RecommendedDish;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationDecisionRepository;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationDishRepository;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationIngredientRepository;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final String AUTHORIZATION = "Bearer raw-access-key";

    @Mock private UserRepository userRepository;
    @Mock private MealRepository mealRepository;
    @Mock private FoodRepository foodRepository;
    @Mock private RecommendationRepository recommendationRepository;
    @Mock private RecommendationIngredientRepository ingredientRepository;
    @Mock private RecommendationDishRepository dishRepository;
    @Mock private RecommendationDecisionRepository decisionRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private RecommendationPlanningService planningService;
    @Mock private RecommendationResponseAssembler responseAssembler;

    private RecommendationService recommendationService;
    private RecommendationPolicyProperties recommendationProperties;
    private User user;

    @BeforeEach
    void setUp() {
        recommendationProperties = new RecommendationPolicyProperties();
        recommendationProperties.setIngredientCount(2);
        recommendationProperties.setMinimumTargetCoveragePercent(value("20"));
        recommendationService = new RecommendationService(
                userRepository,
                mealRepository,
                foodRepository,
                recommendationRepository,
                ingredientRepository,
                dishRepository,
                decisionRepository,
                idempotencyKeyRepository,
                planningService,
                responseAssembler,
                recommendationProperties
        );
        user = User.builder().userId("user-id").build();
    }

    @Test
    void createsRecommendationSnapshotsForConfirmedMeal() {
        Meal meal = confirmedMeal(LocalDateTime.of(2026, 8, 15, 16, 30));
        Recommendation previousRecommendation = recommendation(
                confirmedMeal(LocalDateTime.of(2026, 8, 14, 12, 0))
        );
        RecommendationPlan plan = planWithIngredient();
        RecommendationRes assembled = RecommendationRes.builder()
                .recommendationId("response-id")
                .status(RecommendationStatus.CREATED)
                .build();
        authenticate();
        when(idempotencyKeyRepository.findById("create-key")).thenReturn(Optional.empty());
        when(mealRepository.findOwnedByIdForUpdate(meal.getMealId(), "user-id"))
                .thenReturn(Optional.of(meal));
        reserveNewKey();
        completeReservation();
        when(recommendationRepository.findByMealMealId(meal.getMealId()))
                .thenReturn(Optional.empty());
        when(recommendationRepository
                .findFirstByUserUserIdOrderByCreatedAtDescRecommendationIdDesc("user-id"))
                .thenReturn(Optional.of(previousRecommendation));
        when(ingredientRepository
                .findAllByRecommendationRecommendationIdOrderByRankOrderAsc(
                        previousRecommendation.getRecommendationId()))
                .thenReturn(List.of(ingredient(previousRecommendation)));
        when(planningService.plan(
                user, LocalDate.of(2026, 8, 16), 2, List.of("시금치")))
                .thenReturn(plan);
        when(recommendationRepository.save(any(Recommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(foodRepository.getReferenceById(anyString()))
                .thenAnswer(invocation -> food(invocation.getArgument(0)));
        when(ingredientRepository.save(any(RecommendationIngredient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dishRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(responseAssembler.assemble(any(Recommendation.class))).thenReturn(assembled);

        RecommendationCreationResult result = recommendationService.create(
                AUTHORIZATION,
                "create-key",
                meal.getMealId()
        );

        assertThat(result.createdNow()).isTrue();
        assertThat(result.response()).isSameAs(assembled);
        ArgumentCaptor<Recommendation> captor = ArgumentCaptor.forClass(Recommendation.class);
        verify(recommendationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecommendationDate())
                .isEqualTo(LocalDate.of(2026, 8, 16));
        assertThat(captor.getValue().getStatus()).isEqualTo(RecommendationStatus.CREATED);
        assertThat(captor.getValue().getTargetNutrient()).isEqualTo(TargetNutrient.IRON);
        verify(planningService).plan(
                user, LocalDate.of(2026, 8, 16), 2, List.of("시금치"));
        verify(ingredientRepository, times(2)).save(any(RecommendationIngredient.class));
        verify(dishRepository, times(2)).saveAll(anyList());
        verify(idempotencyKeyRepository).reserve(
                "create-key", "user-id", "RECOMMENDATION_CREATE", fingerprint(meal));
        verify(idempotencyKeyRepository).completeReservation(
                "create-key", captor.getValue().getRecommendationId());
    }

    @Test
    void returnsBalancedMealWhenThereIsNoNutrientGap() {
        Meal meal = confirmedMeal(LocalDateTime.of(2026, 8, 16, 1, 0));
        RecommendationPlan plan = new RecommendationPlan(gap(null), List.of());
        prepareCreation(meal, plan);
        when(responseAssembler.assemble(any(Recommendation.class)))
                .thenAnswer(invocation -> response(invocation.getArgument(0)));

        RecommendationCreationResult result = recommendationService.create(
                AUTHORIZATION,
                "create-key",
                meal.getMealId()
        );

        assertThat(result.response().getStatus()).isEqualTo(RecommendationStatus.NO_CANDIDATE);
        assertThat(result.response().getNoRecommendationReason())
                .isEqualTo(NoRecommendationReason.BALANCED_MEAL);
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    void returnsNoSafeCandidateWhenGapExistsButMappingIsEmpty() {
        Meal meal = confirmedMeal(LocalDateTime.of(2026, 8, 16, 1, 0));
        RecommendationPlan plan = new RecommendationPlan(gap(TargetNutrient.IRON), List.of());
        prepareCreation(meal, plan);
        when(responseAssembler.assemble(any(Recommendation.class)))
                .thenAnswer(invocation -> response(invocation.getArgument(0)));

        RecommendationCreationResult result = recommendationService.create(
                AUTHORIZATION,
                "create-key",
                meal.getMealId()
        );

        assertThat(result.response().getStatus()).isEqualTo(RecommendationStatus.NO_CANDIDATE);
        assertThat(result.response().getNoRecommendationReason())
                .isEqualTo(NoRecommendationReason.NO_SAFE_CANDIDATE);
    }

    @Test
    void refreshesWithExactlyTwoIngredientsAndExcludesAllPreviousNames() {
        Recommendation recommendation = recommendation(confirmedMeal(LocalDateTime.now()));
        RecommendationIngredient spinach = ingredient(recommendation);
        RecommendationIngredient broccoli = RecommendationIngredient.builder()
                .ingredientId("ingredient-id-2")
                .recommendation(recommendation)
                .food(food("broccoli-food"))
                .rankOrder(2)
                .ingredientName("브로콜리")
                .reason("철 보완에 도움이 되는 원재료입니다.")
                .nutritionSnapshot(NutritionValues.builder().ironMg(value("1.2")).build())
                .build();
        RecommendationPlan refreshedPlan = planWithIngredient();
        RecommendationRes assembled = response(recommendation);
        authenticate();
        when(idempotencyKeyRepository.findById("refresh-key")).thenReturn(Optional.empty());
        when(recommendationRepository.findOwnedByIdForUpdate(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        reserveNewKey();
        completeReservation();
        when(ingredientRepository.findAllByRecommendationRecommendationIdOrderByRankOrderAsc(
                recommendation.getRecommendationId()))
                .thenReturn(List.of(spinach, broccoli));
        when(planningService.plan(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 16)),
                org.mockito.ArgumentMatchers.eq(2),
                any(Collection.class)
        )).thenReturn(refreshedPlan);
        when(foodRepository.getReferenceById(anyString()))
                .thenAnswer(invocation -> food(invocation.getArgument(0)));
        when(ingredientRepository.save(any(RecommendationIngredient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dishRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(responseAssembler.assemble(recommendation)).thenReturn(assembled);

        RecommendationRes result = recommendationService.refresh(
                AUTHORIZATION,
                "refresh-key",
                recommendation.getRecommendationId()
        );

        assertThat(result).isSameAs(assembled);
        assertThat(recommendation.getRefreshCount()).isEqualTo(1);
        assertThat(recommendation.getExcludedIngredientNames())
                .containsExactly("시금치", "브로콜리");
        ArgumentCaptor<Collection<String>> exclusions = ArgumentCaptor.forClass(Collection.class);
        verify(planningService).plan(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 16)),
                org.mockito.ArgumentMatchers.eq(2),
                exclusions.capture()
        );
        assertThat(exclusions.getValue()).containsExactly("시금치", "브로콜리");
        verify(dishRepository).deleteAllForRecommendation(recommendation.getRecommendationId());
        verify(ingredientRepository).deleteAllForRecommendation(
                recommendation.getRecommendationId());
        verify(ingredientRepository, times(2)).save(any(RecommendationIngredient.class));
    }

    @Test
    void keepsCurrentRecommendationWhenRefreshCannotFillTwoIngredients() {
        Recommendation recommendation = recommendation(confirmedMeal(LocalDateTime.now()));
        RecommendationIngredient current = ingredient(recommendation);
        authenticate();
        when(idempotencyKeyRepository.findById("refresh-key")).thenReturn(Optional.empty());
        when(recommendationRepository.findOwnedByIdForUpdate(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        reserveNewKey();
        when(ingredientRepository.findAllByRecommendationRecommendationIdOrderByRankOrderAsc(
                recommendation.getRecommendationId()))
                .thenReturn(List.of(current));
        when(planningService.plan(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 16)),
                org.mockito.ArgumentMatchers.eq(2),
                any(Collection.class)
        )).thenReturn(new RecommendationPlan(gap(TargetNutrient.IRON), List.of()));

        assertThatThrownBy(() -> recommendationService.refresh(
                AUTHORIZATION,
                "refresh-key",
                recommendation.getRecommendationId()
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.RECOMMENDATION_REFRESH_EXHAUSTED));

        verify(dishRepository, never()).deleteAllForRecommendation(anyString());
        verify(ingredientRepository, never()).deleteAllForRecommendation(anyString());
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    void replaysExistingRecommendationForSameIdempotencyKey() {
        Meal meal = confirmedMeal(LocalDateTime.of(2026, 8, 16, 1, 0));
        Recommendation recommendation = recommendation(meal);
        RecommendationRes assembled = response(recommendation);
        authenticate();
        String fingerprint = AccessKeyGenerator.hash(
                "RECOMMENDATION_CREATE:" + meal.getMealId()
        );
        when(idempotencyKeyRepository.findById("same-key")).thenReturn(Optional.of(
                idempotency("same-key", "RECOMMENDATION_CREATE", fingerprint,
                        recommendation.getRecommendationId())
        ));
        when(recommendationRepository.findByRecommendationIdAndUserUserId(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        when(responseAssembler.assemble(recommendation)).thenReturn(assembled);

        RecommendationCreationResult result = recommendationService.create(
                AUTHORIZATION,
                "same-key",
                meal.getMealId()
        );

        assertThat(result.createdNow()).isFalse();
        assertThat(result.response()).isSameAs(assembled);
        verify(planningService, never()).plan(any(), any(), anyInt());
    }

    @Test
    void replaysCreationWhenConcurrentRequestWonIdempotencyReservation() {
        Meal meal = confirmedMeal(LocalDateTime.of(2026, 8, 16, 1, 0));
        Recommendation recommendation = recommendation(meal);
        RecommendationRes assembled = response(recommendation);
        String fingerprint = fingerprint(meal);
        IdempotencyKey completedKey = idempotency(
                "same-key",
                "RECOMMENDATION_CREATE",
                fingerprint,
                recommendation.getRecommendationId()
        );
        authenticate();
        when(idempotencyKeyRepository.findById("same-key"))
                .thenReturn(Optional.empty(), Optional.of(completedKey));
        when(mealRepository.findOwnedByIdForUpdate(meal.getMealId(), "user-id"))
                .thenReturn(Optional.of(meal));
        when(idempotencyKeyRepository.reserve(
                "same-key", "user-id", "RECOMMENDATION_CREATE", fingerprint))
                .thenReturn(0);
        when(recommendationRepository.findByRecommendationIdAndUserUserId(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        when(responseAssembler.assemble(recommendation)).thenReturn(assembled);

        RecommendationCreationResult result = recommendationService.create(
                AUTHORIZATION,
                "same-key",
                meal.getMealId()
        );

        assertThat(result.createdNow()).isFalse();
        assertThat(result.response()).isSameAs(assembled);
        verify(planningService, never()).plan(any(), any(), anyInt());
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void rejectsCreationForMealThatIsNotConfirmed() {
        Meal meal = Meal.createText(user, "식사", LocalDateTime.now());
        authenticate();
        when(idempotencyKeyRepository.findById("create-key")).thenReturn(Optional.empty());
        when(mealRepository.findOwnedByIdForUpdate(meal.getMealId(), "user-id"))
                .thenReturn(Optional.of(meal));

        assertThatThrownBy(() -> recommendationService.create(
                AUTHORIZATION,
                "create-key",
                meal.getMealId()
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.RECOMMENDATION_CREATION_CONFLICT));
    }

    @Test
    void selectsIngredientThatBelongsToRecommendation() {
        Meal meal = confirmedMeal(LocalDateTime.now());
        Recommendation recommendation = recommendation(meal);
        RecommendationIngredient ingredient = ingredient(recommendation);
        authenticate();
        when(idempotencyKeyRepository.findById("decision-key")).thenReturn(Optional.empty());
        when(recommendationRepository.findOwnedByIdForUpdate(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        reserveNewKey();
        completeReservation();
        when(ingredientRepository.findByIngredientIdAndRecommendationRecommendationId(
                ingredient.getIngredientId(), recommendation.getRecommendationId()))
                .thenReturn(Optional.of(ingredient));
        when(decisionRepository.save(any(RecommendationDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecommendationDecisionRes result = recommendationService.decide(
                AUTHORIZATION,
                "decision-key",
                recommendation.getRecommendationId(),
                new RecommendationDecisionReq(
                        RecommendationDecisionType.SELECTED,
                        ingredient.getIngredientId()
                )
        );

        assertThat(result.getStatus()).isEqualTo(RecommendationStatus.SELECTED);
        assertThat(result.getSelectedIngredientId()).isEqualTo(ingredient.getIngredientId());
        assertThat(recommendation.getStatus()).isEqualTo(RecommendationStatus.SELECTED);
        verify(idempotencyKeyRepository).completeReservation(
                "decision-key", recommendation.getRecommendationId());
    }

    @Test
    void skipsRecommendationWithoutIngredient() {
        Meal meal = confirmedMeal(LocalDateTime.now());
        Recommendation recommendation = recommendation(meal);
        authenticate();
        when(idempotencyKeyRepository.findById("decision-key")).thenReturn(Optional.empty());
        when(recommendationRepository.findOwnedByIdForUpdate(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        reserveNewKey();
        completeReservation();
        when(decisionRepository.save(any(RecommendationDecision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecommendationDecisionRes result = recommendationService.decide(
                AUTHORIZATION,
                "decision-key",
                recommendation.getRecommendationId(),
                new RecommendationDecisionReq(RecommendationDecisionType.SKIPPED, null)
        );

        assertThat(result.getStatus()).isEqualTo(RecommendationStatus.SKIPPED);
        assertThat(result.getSelectedIngredientId()).isNull();
        assertThat(recommendation.getStatus()).isEqualTo(RecommendationStatus.SKIPPED);
    }

    @ParameterizedTest
    @EnumSource(RecommendationDecisionType.class)
    void replaysDecisionForSameIdempotencyKeyWithoutSavingAgain(
            RecommendationDecisionType decisionType
    ) {
        Meal meal = confirmedMeal(LocalDateTime.now());
        Recommendation recommendation = recommendation(meal);
        RecommendationIngredient ingredient = decisionType == RecommendationDecisionType.SELECTED
                ? ingredient(recommendation)
                : null;
        String ingredientId = ingredient == null ? null : ingredient.getIngredientId();
        RecommendationDecisionReq request = new RecommendationDecisionReq(
                decisionType,
                ingredientId
        );
        LocalDateTime decidedAt = LocalDateTime.of(2026, 8, 17, 1, 30);
        RecommendationDecision savedDecision = RecommendationDecision.builder()
                .recommendation(recommendation)
                .ingredient(ingredient)
                .decision(decisionType)
                .decidedAt(decidedAt)
                .build();
        String fingerprint = AccessKeyGenerator.hash(
                "RECOMMENDATION_DECISION:"
                        + recommendation.getRecommendationId() + ":"
                        + decisionType + ":"
                        + (ingredientId == null ? "" : ingredientId)
        );
        IdempotencyKey completedKey = idempotency(
                "decision-key",
                "RECOMMENDATION_DECISION",
                fingerprint,
                recommendation.getRecommendationId()
        );
        authenticate();
        when(idempotencyKeyRepository.findById("decision-key"))
                .thenReturn(Optional.empty(), Optional.of(completedKey));
        when(recommendationRepository.findOwnedByIdForUpdate(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        when(idempotencyKeyRepository.reserve(
                "decision-key", "user-id", "RECOMMENDATION_DECISION", fingerprint))
                .thenReturn(1);
        when(idempotencyKeyRepository.completeReservation(
                "decision-key", recommendation.getRecommendationId())).thenReturn(1);
        if (ingredient != null) {
            when(ingredientRepository.findByIngredientIdAndRecommendationRecommendationId(
                    ingredientId,
                    recommendation.getRecommendationId()
            )).thenReturn(Optional.of(ingredient));
        }
        when(decisionRepository.save(any(RecommendationDecision.class)))
                .thenReturn(savedDecision);
        when(recommendationRepository.findByRecommendationIdAndUserUserId(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        when(decisionRepository.findById(recommendation.getRecommendationId()))
                .thenReturn(Optional.of(savedDecision));

        RecommendationDecisionRes first = recommendationService.decide(
                AUTHORIZATION,
                "decision-key",
                recommendation.getRecommendationId(),
                request
        );
        RecommendationDecisionRes replay = recommendationService.decide(
                AUTHORIZATION,
                "decision-key",
                recommendation.getRecommendationId(),
                request
        );

        assertThat(replay.getStatus()).isEqualTo(first.getStatus());
        assertThat(replay.getSelectedIngredientId()).isEqualTo(first.getSelectedIngredientId());
        assertThat(replay.getDecidedAt()).isEqualTo(first.getDecidedAt());
        verify(decisionRepository, times(1)).save(any(RecommendationDecision.class));
        verify(recommendationRepository, times(1)).findOwnedByIdForUpdate(
                recommendation.getRecommendationId(), "user-id");
        verify(idempotencyKeyRepository, times(1)).reserve(
                "decision-key", "user-id", "RECOMMENDATION_DECISION", fingerprint);
    }

    @Test
    void replaysDecisionWhenConcurrentRequestWonIdempotencyReservation() {
        Meal meal = confirmedMeal(LocalDateTime.now());
        Recommendation recommendation = recommendation(meal);
        LocalDateTime decidedAt = LocalDateTime.of(2026, 8, 17, 1, 30);
        RecommendationDecision savedDecision = RecommendationDecision.builder()
                .recommendation(recommendation)
                .decision(RecommendationDecisionType.SKIPPED)
                .decidedAt(decidedAt)
                .build();
        recommendation.skip();
        RecommendationDecisionReq request = new RecommendationDecisionReq(
                RecommendationDecisionType.SKIPPED,
                null
        );
        String fingerprint = AccessKeyGenerator.hash(
                "RECOMMENDATION_DECISION:"
                        + recommendation.getRecommendationId()
                        + ":SKIPPED:"
        );
        IdempotencyKey completedKey = idempotency(
                "decision-key",
                "RECOMMENDATION_DECISION",
                fingerprint,
                recommendation.getRecommendationId()
        );
        authenticate();
        when(idempotencyKeyRepository.findById("decision-key"))
                .thenReturn(Optional.empty(), Optional.of(completedKey));
        when(recommendationRepository.findOwnedByIdForUpdate(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        when(idempotencyKeyRepository.reserve(
                "decision-key", "user-id", "RECOMMENDATION_DECISION", fingerprint))
                .thenReturn(0);
        when(recommendationRepository.findByRecommendationIdAndUserUserId(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        when(decisionRepository.findById(recommendation.getRecommendationId()))
                .thenReturn(Optional.of(savedDecision));

        RecommendationDecisionRes result = recommendationService.decide(
                AUTHORIZATION,
                "decision-key",
                recommendation.getRecommendationId(),
                request
        );

        assertThat(result.getStatus()).isEqualTo(RecommendationStatus.SKIPPED);
        assertThat(result.getDecidedAt()).isEqualTo(decidedAt.atOffset(ZoneOffset.UTC));
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void rejectsIngredientThatDoesNotBelongToRecommendation() {
        Meal meal = confirmedMeal(LocalDateTime.now());
        Recommendation recommendation = recommendation(meal);
        authenticate();
        when(idempotencyKeyRepository.findById("decision-key")).thenReturn(Optional.empty());
        when(recommendationRepository.findOwnedByIdForUpdate(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
        reserveNewKey();
        when(ingredientRepository.findByIngredientIdAndRecommendationRecommendationId(
                "other-ingredient", recommendation.getRecommendationId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> recommendationService.decide(
                AUTHORIZATION,
                "decision-key",
                recommendation.getRecommendationId(),
                new RecommendationDecisionReq(
                        RecommendationDecisionType.SELECTED,
                        "other-ingredient"
                )
        )).isInstanceOfSatisfying(BaseException.class, exception ->
                assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ErrorResponseCode.RECOMMENDATION_DECISION_INVALID));
        verify(decisionRepository, never()).save(any());
    }

    private void prepareCreation(Meal meal, RecommendationPlan plan) {
        authenticate();
        when(idempotencyKeyRepository.findById("create-key")).thenReturn(Optional.empty());
        when(mealRepository.findOwnedByIdForUpdate(meal.getMealId(), "user-id"))
                .thenReturn(Optional.of(meal));
        reserveNewKey();
        completeReservation();
        when(recommendationRepository.findByMealMealId(meal.getMealId()))
                .thenReturn(Optional.empty());
        when(planningService.plan(user, LocalDate.of(2026, 8, 16), 2, List.of()))
                .thenReturn(plan);
        when(recommendationRepository.save(any(Recommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void authenticate() {
        when(userRepository.findByAccessKeyHash(anyString())).thenReturn(Optional.of(user));
    }

    private void reserveNewKey() {
        when(idempotencyKeyRepository.reserve(
                anyString(), anyString(), anyString(), anyString())).thenReturn(1);
    }

    private void completeReservation() {
        when(idempotencyKeyRepository.completeReservation(
                anyString(), anyString())).thenReturn(1);
    }

    private Meal confirmedMeal(LocalDateTime eatenAtUtc) {
        Meal meal = Meal.createText(user, "식사", eatenAtUtc);
        meal.markReviewRequired();
        meal.confirm(eatenAtUtc.plusMinutes(10));
        return meal;
    }

    private Recommendation recommendation(Meal meal) {
        return Recommendation.created(
                user,
                meal,
                LocalDate.of(2026, 8, 16),
                TargetNutrient.IRON,
                NutritionValues.builder().proteinG(value("30")).build(),
                NutritionValues.builder().ironMg(value("5")).build()
        );
    }

    private RecommendationIngredient ingredient(Recommendation recommendation) {
        return RecommendationIngredient.builder()
                .ingredientId("ingredient-id")
                .recommendation(recommendation)
                .food(food("ingredient-food"))
                .rankOrder(1)
                .ingredientName("시금치")
                .reason("철 보완에 도움이 되는 원재료입니다.")
                .nutritionSnapshot(NutritionValues.builder().ironMg(value("2.7")).build())
                .build();
    }

    private RecommendationPlan planWithIngredient() {
        RankedIngredient ranked = new RankedIngredient(
                "ingredient-food",
                "시금치",
                1,
                TargetNutrient.IRON,
                value("2.7"),
                value("0.54"),
                value("0.7"),
                NutritionValues.builder().ironMg(value("2.7")).build()
        );
        List<RecommendedDish> dishes = List.of(
                new RecommendedDish("dish-template-1", "dish-food-1", "시금치무침", 1),
                new RecommendedDish("dish-template-2", null, "시금치된장국", 2)
        );
        RankedIngredient second = new RankedIngredient(
                "ingredient-food-2",
                "렌틸콩",
                2,
                TargetNutrient.IRON,
                value("3.3"),
                value("0.66"),
                value("0.8"),
                NutritionValues.builder().ironMg(value("3.3")).build()
        );
        List<RecommendedDish> secondDishes = List.of(
                new RecommendedDish("dish-template-3", null, "렌틸콩 샐러드", 1),
                new RecommendedDish("dish-template-4", null, "렌틸콩 수프", 2)
        );
        return new RecommendationPlan(
                gap(TargetNutrient.IRON),
                List.of(
                        new IngredientDishRecommendation(ranked, dishes),
                        new IngredientDishRecommendation(second, secondDishes)
                )
        );
    }

    private NutritionGapResult gap(TargetNutrient target) {
        Map<TargetNutrient, NutrientGap> gaps = new EnumMap<>(TargetNutrient.class);
        for (TargetNutrient nutrient : TargetNutrient.values()) {
            BigDecimal gapAmount = nutrient == target ? value("5") : BigDecimal.ZERO;
            gaps.put(nutrient, new NutrientGap(value("10"), value("5"), gapAmount,
                    nutrient == target ? value("0.5") : BigDecimal.ZERO));
        }
        KdrReferenceValues reference = new KdrReferenceValues(
                value("65"), value("30"), value("800"), value("8"),
                value("3500"), value("800"), value("100")
        );
        return new NutritionGapResult(
                reference,
                NutritionValues.builder().proteinG(value("30")).build(),
                gaps,
                target
        );
    }

    private RecommendationRes response(Recommendation recommendation) {
        return RecommendationRes.builder()
                .recommendationId(recommendation.getRecommendationId())
                .status(recommendation.getStatus())
                .targetNutrient(recommendation.getTargetNutrient())
                .noRecommendationReason(recommendation.getNoRecommendationReason())
                .build();
    }

    private Food food(String foodId) {
        return Food.builder()
                .foodId(foodId)
                .canonicalName(foodId)
                .normalizedName(foodId)
                .active(true)
                .build();
    }

    private IdempotencyKey idempotency(String key, String operation,
                                       String fingerprint, String resourceId) {
        return IdempotencyKey.builder()
                .idempotencyKey(key)
                .userId("user-id")
                .operation(operation)
                .requestFingerprint(fingerprint)
                .resourceId(resourceId)
                .build();
    }

    private String fingerprint(Meal meal) {
        return AccessKeyGenerator.hash("RECOMMENDATION_CREATE:" + meal.getMealId());
    }

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }
}
