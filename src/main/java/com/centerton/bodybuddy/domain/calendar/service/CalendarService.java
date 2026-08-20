package com.centerton.bodybuddy.domain.calendar.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.calendar.dto.DailyMealsRes;
import com.centerton.bodybuddy.domain.calendar.dto.MonthlyStatsRes;
import com.centerton.bodybuddy.domain.calendar.model.CalendarMealStatus;
import com.centerton.bodybuddy.domain.meal.entity.MealItem;
import com.centerton.bodybuddy.domain.meal.entity.MealNutritionSummary;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.meal.repository.MealItemRepository;
import com.centerton.bodybuddy.domain.meal.repository.MealNutritionSummaryRepository;
import com.centerton.bodybuddy.domain.recommendation.entity.Recommendation;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecision;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecisionType;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDish;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationIngredient;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationStatus;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationDecisionRepository;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationDishRepository;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationIngredientRepository;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final List<MealStatus> CALENDAR_MEAL_STATUSES = List.of(
            MealStatus.CONFIRMED,
            MealStatus.COMPLETED
    );
    private static final long RECOMMENDATION_VALID_HOURS = 24L;

    private final UserRepository userRepository;
    private final MealNutritionSummaryRepository mealNutritionSummaryRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationDecisionRepository recommendationDecisionRepository;
    private final RecommendationIngredientRepository recommendationIngredientRepository;
    private final RecommendationDishRepository recommendationDishRepository;
    private final MealItemRepository mealItemRepository;

    @Transactional(readOnly = true)
    public DailyMealsRes getMealsByDate(String authorization, LocalDate date) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        LocalDateTime startUtc = CalendarTimeMapper.toUtcStartOfDay(date);
        LocalDateTime endUtc = CalendarTimeMapper.toUtcStartOfDay(date.plusDays(1));

        List<MealNutritionSummary> summaries = mealNutritionSummaryRepository.findDailySummaries(
                user.getUserId(),
                CALENDAR_MEAL_STATUSES,
                startUtc,
                endUtc
        );

        Map<String, List<String>> foodNamesByMealId = getFoodNamesByMealId(summaries);

        List<DailyMealsRes.MealInfo> mealInfos = summaries.stream()
                .map(summary -> {
                    NutritionValues nutrition = summary.getNutrition();

                    return DailyMealsRes.MealInfo.builder()
                            .mealId(summary.getMealId())
                            .directInputText(summary.getMeal().getDirectInputText())
                            .photoUrl(createPhotoUrl(summary.getMeal().getPhotoObjectKey()))
                            .foodNames(foodNamesByMealId.getOrDefault(
                                    summary.getMealId(),
                                    List.of()
                            ))
                            .eatenAt(CalendarTimeMapper.toKoreaOffsetDateTime(
                                    summary.getMeal().getEatenAt()
                            ))
                            .calories(nutrition == null ? null : nutrition.getCaloriesKcal())
                            .carbohydrate(nutrition == null ? null : nutrition.getCarbohydrateG())
                            .protein(nutrition == null ? null : nutrition.getProteinG())
                            .fat(nutrition == null ? null : nutrition.getFatG())
                            .recommendedDishName(getTopRecommendedDishName(summary.getMealId()))
                            .build();
                })
                .toList();

        return DailyMealsRes.builder()
                .date(date.toString())
                .meals(mealInfos)
                .build();
    }

    private Map<String, List<String>> getFoodNamesByMealId(
            List<MealNutritionSummary> summaries
    ) {
        if (summaries.isEmpty()) {
            return Map.of();
        }

        List<String> mealIds = summaries.stream()
                .map(MealNutritionSummary::getMealId)
                .toList();

        return mealItemRepository.findFoodNamesByMealIds(mealIds)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> (String) row[0],
                        Collectors.mapping(
                                row -> (String) row[1],
                                Collectors.toList()
                        )
                ));
    }

    private String createPhotoUrl(String photoObjectKey) {
        if (photoObjectKey == null || photoObjectKey.isBlank()) {
            return null;
        }

        if (photoObjectKey.startsWith("/")) {
            return photoObjectKey;
        }

        return "/api/v1/meals/images/" + photoObjectKey;
    }

    private String getTopRecommendedDishName(String mealId) {
        Optional<Recommendation> recommendation = recommendationRepository.findByMealMealId(mealId);
        if (recommendation.isEmpty()) {
            return null;
        }

        Optional<RecommendationDecision> decision = recommendationDecisionRepository
                .findById(recommendation.get().getRecommendationId());

        if (decision.isEmpty()
                || decision.get().getDecision() != RecommendationDecisionType.SELECTED) {
            return null;
        }

        String selectedIngredientId = decision.get().getIngredient().getIngredientId();

        List<RecommendationDish> dishes = recommendationDishRepository
                .findAllForRecommendation(recommendation.get().getRecommendationId());

        return dishes.stream()
                .filter(dish -> dish.getIngredient().getIngredientId().equals(selectedIngredientId))
                .findFirst()
                .map(RecommendationDish::getDishName)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public MonthlyStatsRes getMonthlyStats(String authorization, YearMonth month) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        LocalDateTime monthStartAt = CalendarTimeMapper.toUtcStartOfDay(month.atDay(1));
        LocalDateTime monthEndAt = CalendarTimeMapper.toUtcStartOfDay(
                month.plusMonths(1).atDay(1)
        );
        LocalDateTime calculationStartAt = monthStartAt.minusHours(RECOMMENDATION_VALID_HOURS);

        List<MealNutritionSummary> calculationSummaries =
                mealNutritionSummaryRepository.findDailySummaries(
                        user.getUserId(),
                        CALENDAR_MEAL_STATUSES,
                        calculationStartAt,
                        monthEndAt
                );

        List<MealNutritionSummary> monthlySummaries = calculationSummaries.stream()
                .filter(summary -> !summary.getMeal().getEatenAt().isBefore(monthStartAt))
                .toList();

        List<Recommendation> recommendations = recommendationRepository.findCreatedBetween(
                user.getUserId(),
                calculationStartAt,
                monthEndAt
        );

        Map<String, List<MealItem>> mealItemsByMealId =
                getMealItemsByMealId(calculationSummaries);

        Map<String, Set<FoodReference>> foodsByRecommendationId =
                getFoodsByRecommendationId(recommendations);

        Map<LocalDate, List<MonthlyStatsRes.MealRecord>> recordsByDate =
                createMealRecords(
                        calculationSummaries,
                        recommendations,
                        mealItemsByMealId,
                        foodsByRecommendationId,
                        monthStartAt
                );

        List<MonthlyStatsRes.DayStatus> days = createDayStatuses(recordsByDate);

        BigDecimal totalCalories = monthlySummaries.stream()
                .map(MealNutritionSummary::getNutrition)
                .filter(Objects::nonNull)
                .map(NutritionValues::getCaloriesKcal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCarbohydrate = monthlySummaries.stream()
                .map(MealNutritionSummary::getNutrition)
                .filter(Objects::nonNull)
                .map(NutritionValues::getCarbohydrateG)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProtein = monthlySummaries.stream()
                .map(MealNutritionSummary::getNutrition)
                .filter(Objects::nonNull)
                .map(NutritionValues::getProteinG)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFat = monthlySummaries.stream()
                .map(MealNutritionSummary::getNutrition)
                .filter(Objects::nonNull)
                .map(NutritionValues::getFatG)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int recordedDays = recordsByDate.size();

        BigDecimal averageCalories = calculateAverage(totalCalories, recordedDays);
        BigDecimal averageCarbohydrate = calculateAverage(totalCarbohydrate, recordedDays);
        BigDecimal averageProtein = calculateAverage(totalProtein, recordedDays);
        BigDecimal averageFat = calculateAverage(totalFat, recordedDays);

        return MonthlyStatsRes.builder()
                .month(month.toString())
                .totalCalories(totalCalories)
                .averageCalories(averageCalories)
                .averageCarbohydrate(averageCarbohydrate)
                .averageProtein(averageProtein)
                .averageFat(averageFat)
                .recordedDays(recordedDays)
                .days(days)
                .build();
    }

    private BigDecimal calculateAverage(
            BigDecimal total,
            int recordedDays
    ) {
        if (recordedDays == 0) {
            return BigDecimal.ZERO;
        }

        return total.divide(
                BigDecimal.valueOf(recordedDays),
                2,
                RoundingMode.HALF_UP
        );
    }

    private Map<String, List<MealItem>> getMealItemsByMealId(
            List<MealNutritionSummary> summaries
    ) {
        if (summaries.isEmpty()) {
            return Map.of();
        }

        List<String> mealIds = summaries.stream()
                .map(MealNutritionSummary::getMealId)
                .toList();

        return mealItemRepository.findAllByMealIds(mealIds)
                .stream()
                .collect(Collectors.groupingBy(item -> item.getMeal().getMealId()));
    }

    private Map<String, Set<FoodReference>> getFoodsByRecommendationId(
            List<Recommendation> recommendations
    ) {
        if (recommendations.isEmpty()) {
            return Map.of();
        }

        List<String> recommendationIds = recommendations.stream()
                .map(Recommendation::getRecommendationId)
                .toList();

        List<RecommendationIngredient> ingredients =
                recommendationIngredientRepository.findAllForRecommendations(recommendationIds);

        List<RecommendationDish> dishes =
                recommendationDishRepository.findAllForRecommendations(recommendationIds);

        Map<String, Set<FoodReference>> result = new HashMap<>();

        for (RecommendationIngredient ingredient : ingredients) {
            String recommendationId = ingredient.getRecommendation().getRecommendationId();
            String foodId = ingredient.getFood() == null
                    ? null
                    : ingredient.getFood().getFoodId();

            result.computeIfAbsent(recommendationId, key -> new HashSet<>())
                    .add(new FoodReference(
                            foodId,
                            normalizeFoodName(ingredient.getIngredientName())
                    ));
        }

        for (RecommendationDish dish : dishes) {
            String recommendationId =
                    dish.getIngredient().getRecommendation().getRecommendationId();

            String foodId = dish.getFood() == null
                    ? null
                    : dish.getFood().getFoodId();

            result.computeIfAbsent(recommendationId, key -> new HashSet<>())
                    .add(new FoodReference(
                            foodId,
                            normalizeFoodName(dish.getDishName())
                    ));
        }

        return result;
    }

    private Map<LocalDate, List<MonthlyStatsRes.MealRecord>> createMealRecords(
            List<MealNutritionSummary> summaries,
            List<Recommendation> recommendations,
            Map<String, List<MealItem>> mealItemsByMealId,
            Map<String, Set<FoodReference>> foodsByRecommendationId,
            LocalDateTime monthStartAt
    ) {
        List<MealNutritionSummary> orderedSummaries = summaries.stream()
                .sorted(Comparator
                        .comparing((MealNutritionSummary summary) ->
                                summary.getMeal().getEatenAt())
                        .thenComparing(MealNutritionSummary::getMealId))
                .toList();

        Set<String> consumedRecommendationIds = new HashSet<>();
        Map<LocalDate, List<MonthlyStatsRes.MealRecord>> recordsByDate = new TreeMap<>();

        for (MealNutritionSummary summary : orderedSummaries) {
            LocalDateTime eatenAt = summary.getMeal().getEatenAt();

            List<Recommendation> activeRecommendations = getActiveRecommendations(
                    recommendations,
                    consumedRecommendationIds,
                    foodsByRecommendationId,
                    eatenAt
            );

            List<MealItem> mealItems = mealItemsByMealId.getOrDefault(
                    summary.getMealId(),
                    List.of()
            );

            List<Recommendation> matchedRecommendations = activeRecommendations.stream()
                    .filter(recommendation -> matchesRecommendation(
                            mealItems,
                            foodsByRecommendationId.getOrDefault(
                                    recommendation.getRecommendationId(),
                                    Set.of()
                            )
                    ))
                    .toList();

            CalendarMealStatus status;

            if (activeRecommendations.isEmpty()) {
                status = CalendarMealStatus.RECORD_ONLY;
            } else if (matchedRecommendations.isEmpty()) {
                status = CalendarMealStatus.RECOMMENDATION_MISSED;
            } else {
                status = CalendarMealStatus.RECOMMENDATION_FOLLOWED;

                matchedRecommendations.stream()
                        .map(Recommendation::getRecommendationId)
                        .forEach(consumedRecommendationIds::add);
            }

            if (eatenAt.isBefore(monthStartAt)) {
                continue;
            }

            MonthlyStatsRes.MealRecord record = MonthlyStatsRes.MealRecord.builder()
                    .mealId(summary.getMealId())
                    .eatenAt(CalendarTimeMapper.toKoreaOffsetDateTime(eatenAt))
                    .status(status)
                    .build();

            recordsByDate.computeIfAbsent(
                    CalendarTimeMapper.toKoreaDate(eatenAt),
                    key -> new ArrayList<>()
            ).add(record);
        }

        return recordsByDate;
    }

    private List<Recommendation> getActiveRecommendations(
            List<Recommendation> recommendations,
            Set<String> consumedRecommendationIds,
            Map<String, Set<FoodReference>> foodsByRecommendationId,
            LocalDateTime eatenAt
    ) {
        LocalDateTime validFrom = eatenAt.minusHours(RECOMMENDATION_VALID_HOURS);

        return recommendations.stream()
                .filter(recommendation ->
                        recommendation.getStatus() != RecommendationStatus.NO_CANDIDATE)
                .filter(recommendation ->
                        !consumedRecommendationIds.contains(
                                recommendation.getRecommendationId()
                        ))
                .filter(recommendation -> recommendation.getCreatedAt() != null)
                .filter(recommendation ->
                        !recommendation.getCreatedAt().isBefore(validFrom))
                .filter(recommendation ->
                        !recommendation.getCreatedAt().isAfter(eatenAt))
                .filter(recommendation ->
                        !foodsByRecommendationId.getOrDefault(
                                recommendation.getRecommendationId(),
                                Set.of()
                        ).isEmpty())
                .toList();
    }

    private boolean matchesRecommendation(
            List<MealItem> mealItems,
            Set<FoodReference> recommendedFoods
    ) {
        return mealItems.stream()
                .anyMatch(mealItem -> recommendedFoods.stream()
                        .anyMatch(recommendedFood ->
                                matchesFood(mealItem, recommendedFood)));
    }

    private boolean matchesFood(
            MealItem mealItem,
            FoodReference recommendedFood
    ) {
        if (mealItem.getFoodId() != null
                && recommendedFood.foodId() != null
                && mealItem.getFoodId().equals(recommendedFood.foodId())) {
            return true;
        }

        String mealFoodName = normalizeFoodName(mealItem.getFoodName());

        return !mealFoodName.isBlank()
                && mealFoodName.equals(recommendedFood.normalizedName());
    }

    private String normalizeFoodName(String foodName) {
        if (foodName == null) {
            return "";
        }

        return foodName.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_-]", "");
    }

    private List<MonthlyStatsRes.DayStatus> createDayStatuses(
            Map<LocalDate, List<MonthlyStatsRes.MealRecord>> recordsByDate
    ) {
        return recordsByDate.entrySet()
                .stream()
                .map(entry -> {
                    List<MonthlyStatsRes.MealRecord> records = entry.getValue();

                    int followedCount = (int) records.stream()
                            .filter(record ->
                                    record.getStatus()
                                            == CalendarMealStatus.RECOMMENDATION_FOLLOWED)
                            .count();

                    int missedCount = (int) records.stream()
                            .filter(record ->
                                    record.getStatus()
                                            == CalendarMealStatus.RECOMMENDATION_MISSED)
                            .count();

                    return MonthlyStatsRes.DayStatus.builder()
                            .date(entry.getKey().toString())
                            .mealCount(records.size())
                            .selectedRecommendationCount(followedCount)
                            .unselectedRecommendationCount(missedCount)
                            .records(records)
                            .build();
                })
                .toList();
    }

    private record FoodReference(
            String foodId,
            String normalizedName
    ) {
    }
}
