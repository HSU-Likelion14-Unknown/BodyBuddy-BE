package com.centerton.bodybuddy.domain.calendar.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.calendar.dto.DailyMealsRes;
import com.centerton.bodybuddy.domain.calendar.dto.MonthlyStatsRes;
import com.centerton.bodybuddy.domain.meal.entity.MealNutritionSummary;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.meal.repository.MealItemRepository;
import com.centerton.bodybuddy.domain.meal.repository.MealNutritionSummaryRepository;
import com.centerton.bodybuddy.domain.recommendation.entity.Recommendation;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecision;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDecisionType;
import com.centerton.bodybuddy.domain.recommendation.entity.RecommendationDish;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationDecisionRepository;
import com.centerton.bodybuddy.domain.recommendation.repository.RecommendationDishRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final UserRepository userRepository;
    private final MealNutritionSummaryRepository mealNutritionSummaryRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationDecisionRepository recommendationDecisionRepository;
    private final RecommendationDishRepository recommendationDishRepository;
    private final MealItemRepository mealItemRepository;

    @Transactional(readOnly = true)
    public DailyMealsRes getMealsByDate(String authorization, LocalDate date) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        LocalDateTime startUtc = date.atStartOfDay();
        LocalDateTime endUtc = date.plusDays(1).atStartOfDay();

        List<MealNutritionSummary> summaries = mealNutritionSummaryRepository.findDailySummaries(
                user.getUserId(),
                List.of(MealStatus.COMPLETED),
                startUtc,
                endUtc
        );

        Map<String, List<String>> foodNamesByMealId =
                getFoodNamesByMealId(summaries);

        List<DailyMealsRes.MealInfo> mealInfos = summaries.stream()
                .map(s -> {
                    NutritionValues nutrition = s.getNutrition();

                    return DailyMealsRes.MealInfo.builder()
                            .mealId(s.getMealId())
                            .directInputText(s.getMeal().getDirectInputText())
                            .photoUrl(createPhotoUrl(s.getMeal().getPhotoObjectKey()))
                            .foodNames(foodNamesByMealId.getOrDefault(
                                    s.getMealId(),
                                    List.of()
                            ))
                            .eatenAt(s.getMeal().getEatenAt())
                            .calories(nutrition == null ? null : nutrition.getCaloriesKcal())
                            .carbohydrate(nutrition == null ? null : nutrition.getCarbohydrateG())
                            .protein(nutrition == null ? null : nutrition.getProteinG())
                            .fat(nutrition == null ? null : nutrition.getFatG())
                            .recommendedDishName(getTopRecommendedDishName(s.getMealId()))
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
                .collect(
                        Collectors.groupingBy(
                                row -> (String) row[0],
                                Collectors.mapping(
                                        row -> (String) row[1],
                                        Collectors.toList()
                                )
                        )
                );
    }

    private String createPhotoUrl(String photoObjectKey) {
        if (photoObjectKey == null || photoObjectKey.isBlank()) {
            return null;
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

        if (decision.isEmpty() || decision.get().getDecision() != RecommendationDecisionType.SELECTED) {
            return null;
        }

        String selectedIngredientId = decision.get().getIngredient().getIngredientId();

        List<RecommendationDish> dishes = recommendationDishRepository
                .findAllForRecommendation(recommendation.get().getRecommendationId());

        return dishes.stream()
                .filter(d -> d.getIngredient().getIngredientId().equals(selectedIngredientId))
                .findFirst()
                .map(RecommendationDish::getDishName)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public MonthlyStatsRes getMonthlyStats(String authorization, YearMonth month) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        LocalDateTime startUtc = month.atDay(1).atStartOfDay();
        LocalDateTime endUtc = month.plusMonths(1).atDay(1).atStartOfDay();

        List<MealNutritionSummary> summaries = mealNutritionSummaryRepository.findDailySummaries(
                user.getUserId(),
                List.of(MealStatus.COMPLETED),
                startUtc,
                endUtc
        );

        BigDecimal totalCalories = summaries.stream()
                .map(MealNutritionSummary::getNutrition)
                .filter(Objects::nonNull)
                .map(NutritionValues::getCaloriesKcal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long recordedDays = summaries.stream()
                .map(s -> s.getMeal().getEatenAt().toLocalDate())
                .distinct()
                .count();

        BigDecimal averageCalories = recordedDays == 0
                ? BigDecimal.ZERO
                : totalCalories.divide(BigDecimal.valueOf(recordedDays), 2, RoundingMode.HALF_UP);

        return MonthlyStatsRes.builder()
                .month(month.toString())
                .totalCalories(totalCalories)
                .averageCalories(averageCalories)
                .recordedDays((int) recordedDays)
                .build();
    }
}