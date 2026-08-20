package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.auth.entity.IdempotencyKey;
import com.centerton.bodybuddy.domain.auth.repository.IdempotencyKeyRepository;
import com.centerton.bodybuddy.domain.auth.util.AccessKeyGenerator;
import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.food.repository.FoodRepository;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
import com.centerton.bodybuddy.domain.recommendation.config.RecommendationPolicyProperties;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationDecisionReq;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationDecisionRes;
import com.centerton.bodybuddy.domain.recommendation.dto.RecommendationRes;
import com.centerton.bodybuddy.domain.recommendation.entity.NoRecommendationReason;
import com.centerton.bodybuddy.domain.recommendation.entity.Recommendation;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecision;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecisionType;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDish;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationIngredient;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationStatus;
import com.centerton.bodybuddy.domain.recommendation.model.IngredientDishRecommendation;
import com.centerton.bodybuddy.domain.recommendation.model.NutrientGap;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final String RECOMMENDATION_CREATE = "RECOMMENDATION_CREATE";
    private static final String RECOMMENDATION_REFRESH = "RECOMMENDATION_REFRESH";
    private static final String RECOMMENDATION_DECISION = "RECOMMENDATION_DECISION";
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final MealRepository mealRepository;
    private final FoodRepository foodRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationIngredientRepository ingredientRepository;
    private final RecommendationDishRepository dishRepository;
    private final RecommendationDecisionRepository decisionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final RecommendationPlanningService planningService;
    private final RecommendationResponseAssembler responseAssembler;
    private final RecommendationPolicyProperties recommendationProperties;

    @Transactional
    public RecommendationCreationResult create(String authorization,
                                               String idempotencyKey,
                                               String mealId) {
        User user = authenticatedUser(authorization);
        String fingerprint = AccessKeyGenerator.hash(RECOMMENDATION_CREATE + ":" + mealId);
        RecommendationCreationResult previous = findIdempotentCreation(
                idempotencyKey,
                user,
                fingerprint
        );
        if (previous != null) {
            return previous;
        }

        Meal meal = mealRepository.findOwnedByIdForUpdate(mealId, user.getUserId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_NOT_FOUND));
        if (meal.getStatus() != MealStatus.CONFIRMED
                && meal.getStatus() != MealStatus.COMPLETED) {
            throw new BaseException(ErrorResponseCode.RECOMMENDATION_CREATION_CONFLICT);
        }
        if (!reserveIdempotencyKey(
                idempotencyKey,
                user,
                RECOMMENDATION_CREATE,
                fingerprint
        )) {
            return requireIdempotentCreation(idempotencyKey, user, fingerprint);
        }
        if (recommendationRepository.findByMealMealId(mealId).isPresent()) {
            throw new BaseException(ErrorResponseCode.RECOMMENDATION_ALREADY_EXISTS);
        }

        LocalDate recommendationDate = kstDate(meal.getEatenAt());
        List<String> previousIngredientNames = latestRecommendationIngredientNames(
                user.getUserId()
        );
        RecommendationPlan plan = planningService.plan(
                user,
                recommendationDate,
                recommendationProperties.getIngredientCount(),
                previousIngredientNames
        );
        Recommendation recommendation = saveRecommendation(user, meal, recommendationDate, plan);
        completeIdempotencyKey(
                idempotencyKey,
                recommendation.getRecommendationId()
        );
        return new RecommendationCreationResult(
                responseAssembler.assemble(recommendation),
                true
        );
    }

    private List<String> latestRecommendationIngredientNames(String userId) {
        return recommendationRepository
                .findFirstByUserUserIdOrderByCreatedAtDescRecommendationIdDesc(userId)
                .map(Recommendation::getRecommendationId)
                .map(ingredientRepository
                        ::findAllByRecommendationRecommendationIdOrderByRankOrderAsc)
                .orElseGet(List::of)
                .stream()
                .map(RecommendationIngredient::getIngredientName)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    @Transactional
    public RecommendationRes refresh(String authorization,
                                     String idempotencyKey,
                                     String recommendationId) {
        User user = authenticatedUser(authorization);
        String fingerprint = AccessKeyGenerator.hash(
                RECOMMENDATION_REFRESH + ":" + recommendationId
        );
        RecommendationRes previous = findIdempotentRefresh(
                idempotencyKey,
                user,
                fingerprint
        );
        if (previous != null) {
            return previous;
        }

        Recommendation recommendation = recommendationRepository
                .findOwnedByIdForUpdate(recommendationId, user.getUserId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.RECOMMENDATION_NOT_FOUND));
        if (!reserveIdempotencyKey(
                idempotencyKey,
                user,
                RECOMMENDATION_REFRESH,
                fingerprint
        )) {
            return requireIdempotentRefresh(idempotencyKey, user, fingerprint);
        }
        if (recommendation.getStatus() != RecommendationStatus.CREATED) {
            throw new BaseException(ErrorResponseCode.RECOMMENDATION_REFRESH_CONFLICT);
        }

        if (recommendation.getRefreshCount() >= recommendationProperties.getMaxRefreshCount()) {
            throw new BaseException(ErrorResponseCode.RECOMMENDATION_REFRESH_EXHAUSTED);
        }

        List<RecommendationIngredient> currentIngredients = ingredientRepository
                .findAllByRecommendationRecommendationIdOrderByRankOrderAsc(
                        recommendationId
                );
        List<String> currentNames = currentIngredients.stream()
                .map(RecommendationIngredient::getIngredientName)
                .toList();
        List<String> exclusions = new java.util.ArrayList<>(
                recommendation.getExcludedIngredientNames()
        );
        exclusions.addAll(currentNames);

        RecommendationPlan plan = planningService.plan(
                user,
                recommendation.getRecommendationDate(),
                recommendationProperties.getIngredientCount(),
                exclusions
        );
        if (plan.ingredients().size() != recommendationProperties.getIngredientCount()) {
            throw new BaseException(ErrorResponseCode.RECOMMENDATION_REFRESH_EXHAUSTED);
        }

        dishRepository.deleteAllForRecommendation(recommendationId);
        ingredientRepository.deleteAllForRecommendation(recommendationId);
        NutritionGapResult gap = plan.nutritionGap();
        recommendation.refresh(
                gap.target().orElse(null),
                gap.dailyNutrition(),
                gapSnapshot(gap),
                currentNames
        );
        saveIngredients(recommendation, plan.ingredients(), gap.target().orElse(null));
        completeIdempotencyKey(idempotencyKey, recommendationId);
        return responseAssembler.assemble(recommendation);
    }

    @Transactional
    public RecommendationDecisionRes decide(String authorization,
                                            String idempotencyKey,
                                            String recommendationId,
                                            RecommendationDecisionReq request) {
        User user = authenticatedUser(authorization);
        if (request == null || request.getDecision() == null) {
            throw new BaseException(ErrorResponseCode.RECOMMENDATION_DECISION_INVALID);
        }
        String ingredientId = trimToNull(request.getIngredientId());
        String fingerprint = decisionFingerprint(recommendationId, request, ingredientId);
        RecommendationDecisionRes previous = findIdempotentDecision(
                idempotencyKey,
                user,
                fingerprint
        );
        if (previous != null) {
            return previous;
        }

        Recommendation recommendation = recommendationRepository
                .findOwnedByIdForUpdate(recommendationId, user.getUserId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.RECOMMENDATION_NOT_FOUND));
        if (!reserveIdempotencyKey(
                idempotencyKey,
                user,
                RECOMMENDATION_DECISION,
                fingerprint
        )) {
            return requireIdempotentDecision(idempotencyKey, user, fingerprint);
        }
        if (recommendation.getStatus() != RecommendationStatus.CREATED) {
            throw new BaseException(ErrorResponseCode.RECOMMENDATION_DECISION_CONFLICT);
        }

        RecommendationIngredient selectedIngredient = validateDecision(
                recommendationId,
                request.getDecision(),
                ingredientId
        );
        LocalDateTime decidedAt = LocalDateTime.now(ZoneOffset.UTC);
        RecommendationDecision decision = decisionRepository.save(
                RecommendationDecision.builder()
                        .recommendation(recommendation)
                        .ingredient(selectedIngredient)
                        .decision(request.getDecision())
                        .decidedAt(decidedAt)
                        .build()
        );
        if (request.getDecision() == RecommendationDecisionType.SELECTED) {
            recommendation.select();
        } else {
            recommendation.skip();
        }
        completeIdempotencyKey(
                idempotencyKey,
                recommendationId
        );
        return decisionResponse(decision);
    }

    @Transactional(readOnly = true)
    public RecommendationDecisionRes getDecision(String authorization,
                                                 String recommendationId) {
        User user = authenticatedUser(authorization);
        Recommendation recommendation = recommendationRepository
                .findByRecommendationIdAndUserUserId(recommendationId, user.getUserId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.RECOMMENDATION_NOT_FOUND));
        RecommendationDecision decision = decisionRepository
                .findById(recommendation.getRecommendationId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.RECOMMENDATION_NOT_FOUND));
        return decisionResponse(decision);
    }

    private Recommendation saveRecommendation(User user, Meal meal, LocalDate date,
                                              RecommendationPlan plan) {
        NutritionGapResult gap = plan.nutritionGap();
        NutritionValues gapSnapshot = gapSnapshot(gap);
        TargetNutrient target = gap.target().orElse(null);
        Recommendation recommendation;
        if (plan.ingredients().isEmpty()) {
            NoRecommendationReason reason = target == null
                    ? NoRecommendationReason.BALANCED_MEAL
                    : NoRecommendationReason.NO_SAFE_CANDIDATE;
            recommendation = Recommendation.noCandidate(
                    user,
                    meal,
                    date,
                    target,
                    reason,
                    gap.dailyNutrition(),
                    gapSnapshot
            );
        } else {
            recommendation = Recommendation.created(
                    user,
                    meal,
                    date,
                    target,
                    gap.dailyNutrition(),
                    gapSnapshot
            );
        }
        recommendationRepository.save(recommendation);
        saveIngredients(recommendation, plan.ingredients(), target);
        return recommendation;
    }

    private void saveIngredients(Recommendation recommendation,
                                 List<IngredientDishRecommendation> plannedIngredients,
                                 TargetNutrient target) {
        for (int index = 0; index < plannedIngredients.size(); index++) {
            IngredientDishRecommendation planned = plannedIngredients.get(index);
            String foodId = planned.rankedIngredient().foodId();
            Food food = foodId == null ? null : foodRepository.getReferenceById(foodId);
            RecommendationIngredient ingredient = ingredientRepository.save(
                    RecommendationIngredient.builder()
                            .ingredientId(UUID.randomUUID().toString())
                            .recommendation(recommendation)
                            .food(food)
                            .rankOrder(index + 1)
                            .ingredientName(planned.rankedIngredient().ingredientName())
                            .reason(reason(target))
                            .nutritionSnapshot(planned.rankedIngredient().nutritionPerServing())
                            .build()
            );
            saveDishes(ingredient, planned.dishes());
        }
    }

    private void saveDishes(RecommendationIngredient ingredient,
                            List<RecommendedDish> dishes) {
        if (dishes.size() < 2 || dishes.size() > 3) {
            throw new BaseException(ErrorResponseCode.SERVER_ERROR);
        }
        List<RecommendationDish> snapshots = dishes.stream()
                .map(dish -> RecommendationDish.builder()
                        .recommendationDishId(UUID.randomUUID().toString())
                        .ingredient(ingredient)
                        .food(dish.foodId() == null
                                ? null
                                : foodRepository.getReferenceById(dish.foodId()))
                        .dishName(dish.dishName())
                        .rankOrder(dish.rank())
                        .build())
                .toList();
        dishRepository.saveAll(snapshots);
    }

    private RecommendationIngredient validateDecision(
            String recommendationId,
            RecommendationDecisionType decision,
            String ingredientId
    ) {
        if (decision == RecommendationDecisionType.SELECTED) {
            if (ingredientId == null) {
                throw new BaseException(ErrorResponseCode.RECOMMENDATION_DECISION_INVALID);
            }
            return ingredientRepository
                    .findByIngredientIdAndRecommendationRecommendationId(
                            ingredientId,
                            recommendationId
                    )
                    .orElseThrow(() -> new BaseException(
                            ErrorResponseCode.RECOMMENDATION_DECISION_INVALID
                    ));
        }
        if (ingredientId != null) {
            throw new BaseException(ErrorResponseCode.RECOMMENDATION_DECISION_INVALID);
        }
        return null;
    }

    private RecommendationCreationResult findIdempotentCreation(
            String key,
            User user,
            String fingerprint
    ) {
        IdempotencyKey record = idempotencyKeyRepository.findById(key).orElse(null);
        if (record == null) {
            return null;
        }
        requireSameIdempotentRequest(record, user, RECOMMENDATION_CREATE, fingerprint);
        Recommendation recommendation = recommendationRepository
                .findByRecommendationIdAndUserUserId(
                        record.getResourceId(),
                        user.getUserId()
                )
                .orElseThrow(() -> new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED));
        return new RecommendationCreationResult(
                responseAssembler.assemble(recommendation),
                false
        );
    }

    private RecommendationCreationResult requireIdempotentCreation(
            String key,
            User user,
            String fingerprint
    ) {
        RecommendationCreationResult result = findIdempotentCreation(key, user, fingerprint);
        if (result == null) {
            throw new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED);
        }
        return result;
    }

    private RecommendationRes findIdempotentRefresh(
            String key,
            User user,
            String fingerprint
    ) {
        IdempotencyKey record = idempotencyKeyRepository.findById(key).orElse(null);
        if (record == null) {
            return null;
        }
        requireSameIdempotentRequest(record, user, RECOMMENDATION_REFRESH, fingerprint);
        Recommendation recommendation = recommendationRepository
                .findByRecommendationIdAndUserUserId(
                        record.getResourceId(),
                        user.getUserId()
                )
                .orElseThrow(() -> new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED));
        return responseAssembler.assemble(recommendation);
    }

    private RecommendationRes requireIdempotentRefresh(
            String key,
            User user,
            String fingerprint
    ) {
        RecommendationRes result = findIdempotentRefresh(key, user, fingerprint);
        if (result == null) {
            throw new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED);
        }
        return result;
    }

    private RecommendationDecisionRes findIdempotentDecision(
            String key,
            User user,
            String fingerprint
    ) {
        IdempotencyKey record = idempotencyKeyRepository.findById(key).orElse(null);
        if (record == null) {
            return null;
        }
        requireSameIdempotentRequest(record, user, RECOMMENDATION_DECISION, fingerprint);
        Recommendation recommendation = recommendationRepository
                .findByRecommendationIdAndUserUserId(
                        record.getResourceId(),
                        user.getUserId()
                )
                .orElseThrow(() -> new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED));
        RecommendationDecision decision = decisionRepository
                .findById(recommendation.getRecommendationId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED));
        return decisionResponse(decision);
    }

    private RecommendationDecisionRes requireIdempotentDecision(
            String key,
            User user,
            String fingerprint
    ) {
        RecommendationDecisionRes result = findIdempotentDecision(key, user, fingerprint);
        if (result == null) {
            throw new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED);
        }
        return result;
    }

    private void requireSameIdempotentRequest(IdempotencyKey record, User user,
                                              String operation, String fingerprint) {
        boolean same = user.getUserId().equals(record.getUserId())
                && operation.equals(record.getOperation())
                && fingerprint.equals(record.getRequestFingerprint())
                && record.getResourceId() != null;
        if (!same) {
            throw new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private boolean reserveIdempotencyKey(String key, User user,
                                          String operation, String fingerprint) {
        return idempotencyKeyRepository.reserve(
                key,
                user.getUserId(),
                operation,
                fingerprint
        ) == 1;
    }

    private void completeIdempotencyKey(String key, String resourceId) {
        if (idempotencyKeyRepository.completeReservation(key, resourceId) != 1) {
            throw new BaseException(ErrorResponseCode.SERVER_ERROR);
        }
    }

    private RecommendationDecisionRes decisionResponse(RecommendationDecision decision) {
        return RecommendationDecisionRes.builder()
                .recommendationId(decision.getRecommendation().getRecommendationId())
                .status(decision.getDecision() == RecommendationDecisionType.SELECTED
                        ? RecommendationStatus.SELECTED
                        : RecommendationStatus.SKIPPED)
                .selectedIngredientId(decision.getIngredient() == null
                        ? null
                        : decision.getIngredient().getIngredientId())
                .decidedAt(decision.getDecidedAt().atOffset(ZoneOffset.UTC))
                .build();
    }

    private NutritionValues gapSnapshot(NutritionGapResult result) {
        return NutritionValues.builder()
                .proteinG(gapAmount(result, TargetNutrient.PROTEIN))
                .fiberG(gapAmount(result, TargetNutrient.FIBER))
                .calciumMg(gapAmount(result, TargetNutrient.CALCIUM))
                .ironMg(gapAmount(result, TargetNutrient.IRON))
                .potassiumMg(gapAmount(result, TargetNutrient.POTASSIUM))
                .vitaminAMcgRae(gapAmount(result, TargetNutrient.VITAMIN_A))
                .vitaminCMg(gapAmount(result, TargetNutrient.VITAMIN_C))
                .build();
    }

    private BigDecimal gapAmount(NutritionGapResult result, TargetNutrient nutrient) {
        NutrientGap gap = result.gapOf(nutrient);
        return gap == null ? BigDecimal.ZERO : gap.gapAmount();
    }

    private String reason(TargetNutrient nutrient) {
        String name = switch (nutrient) {
            case PROTEIN -> "단백질";
            case FIBER -> "식이섬유";
            case CALCIUM -> "칼슘";
            case IRON -> "철";
            case POTASSIUM -> "칼륨";
            case VITAMIN_A -> "비타민 A";
            case VITAMIN_C -> "비타민 C";
        };
        return name + " 보완에 도움이 되는 원재료입니다.";
    }

    private String decisionFingerprint(String recommendationId,
                                       RecommendationDecisionReq request,
                                       String ingredientId) {
        return AccessKeyGenerator.hash(
                RECOMMENDATION_DECISION + ":"
                        + recommendationId + ":"
                        + request.getDecision() + ":"
                        + (ingredientId == null ? "" : ingredientId)
        );
    }

    private User authenticatedUser(String authorization) {
        return AuthValidator.validateAndGetUser(authorization, userRepository);
    }

    private LocalDate kstDate(LocalDateTime eatenAtUtc) {
        return eatenAtUtc.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(KOREA_ZONE)
                .toLocalDate();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
