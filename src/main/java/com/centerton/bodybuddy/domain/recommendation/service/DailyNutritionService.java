package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.entity.MealNutritionSummary;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.meal.repository.MealNutritionSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class DailyNutritionService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<MealStatus> INCLUDED_STATUSES = Set.of(
            MealStatus.CONFIRMED,
            MealStatus.COMPLETED
    );

    private final MealNutritionSummaryRepository nutritionSummaryRepository;

    @Transactional(readOnly = true)
    public NutritionValues sumForDate(String userId, LocalDate date) {
        LocalDateTime startUtc = date.atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        LocalDateTime endUtc = date.plusDays(1).atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        List<NutritionValues> values = nutritionSummaryRepository.findDailySummaries(
                        userId,
                        INCLUDED_STATUSES,
                        startUtc,
                        endUtc
                ).stream()
                .map(MealNutritionSummary::getNutrition)
                .toList();
        return sum(values);
    }

    private NutritionValues sum(List<NutritionValues> values) {
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
                .filter(java.util.Objects::nonNull)
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
