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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private User user;

    @BeforeEach
    void setUp() {
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
                responseAssembler
        );
        user = User.builder().userId("user-id").build();
    }

    @Test
    void createsRecommendationSnapshotsForConfirmedMeal() {
        Meal meal = confirmedMeal(LocalDateTime.of(2026, 8, 15, 16, 30));
        RecommendationPlan plan = planWithIngredient();
        RecommendationRes assembled = RecommendationRes.builder()
                .recommendationId("response-id")
                .status(RecommendationStatus.CREATED)
                .build();
        authenticate();
        when(idempotencyKeyRepository.findById("create-key")).thenReturn(Optional.empty());
        when(mealRepository.findByMealIdAndUserUserId(meal.getMealId(), "user-id"))
                .thenReturn(Optional.of(meal));
        when(recommendationRepository.findByMealMealId(meal.getMealId()))
                .thenReturn(Optional.empty());
        when(planningService.plan(user, LocalDate.of(2026, 8, 16), 3)).thenReturn(plan);
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
        verify(ingredientRepository).save(any(RecommendationIngredient.class));
        verify(dishRepository).saveAll(anyList());
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
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
        verify(planningService, never()).plan(any(), any(), any(Integer.class));
    }

    @Test
    void rejectsCreationForMealThatIsNotConfirmed() {
        Meal meal = Meal.createText(user, "식사", LocalDateTime.now());
        authenticate();
        when(idempotencyKeyRepository.findById("create-key")).thenReturn(Optional.empty());
        when(mealRepository.findByMealIdAndUserUserId(meal.getMealId(), "user-id"))
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
        when(recommendationRepository.findByRecommendationIdAndUserUserId(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
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
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
    }

    @Test
    void skipsRecommendationWithoutIngredient() {
        Meal meal = confirmedMeal(LocalDateTime.now());
        Recommendation recommendation = recommendation(meal);
        authenticate();
        when(idempotencyKeyRepository.findById("decision-key")).thenReturn(Optional.empty());
        when(recommendationRepository.findByRecommendationIdAndUserUserId(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
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

    @Test
    void rejectsIngredientThatDoesNotBelongToRecommendation() {
        Meal meal = confirmedMeal(LocalDateTime.now());
        Recommendation recommendation = recommendation(meal);
        authenticate();
        when(idempotencyKeyRepository.findById("decision-key")).thenReturn(Optional.empty());
        when(recommendationRepository.findByRecommendationIdAndUserUserId(
                recommendation.getRecommendationId(), "user-id"))
                .thenReturn(Optional.of(recommendation));
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
        when(mealRepository.findByMealIdAndUserUserId(meal.getMealId(), "user-id"))
                .thenReturn(Optional.of(meal));
        when(recommendationRepository.findByMealMealId(meal.getMealId()))
                .thenReturn(Optional.empty());
        when(planningService.plan(user, LocalDate.of(2026, 8, 16), 3)).thenReturn(plan);
        when(recommendationRepository.save(any(Recommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void authenticate() {
        when(userRepository.findByAccessKeyHash(anyString())).thenReturn(Optional.of(user));
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
        return new RecommendationPlan(
                gap(TargetNutrient.IRON),
                List.of(new IngredientDishRecommendation(ranked, dishes))
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

    private BigDecimal value(String value) {
        return new BigDecimal(value);
    }
}
