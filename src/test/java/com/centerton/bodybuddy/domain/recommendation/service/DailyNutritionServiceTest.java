package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.entity.MealNutritionSummary;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import com.centerton.bodybuddy.domain.meal.entity.NutritionBasis;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.meal.repository.MealNutritionSummaryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyNutritionServiceTest {

    @Mock
    private MealNutritionSummaryRepository nutritionSummaryRepository;

    @Test
    @SuppressWarnings("unchecked")
    void sumsConfirmedAndCompletedMealsWithinKoreaCalendarDay() {
        DailyNutritionService service = new DailyNutritionService(nutritionSummaryRepository);
        when(nutritionSummaryRepository.findDailySummaries(
                eq("user-id"),
                any(),
                any(),
                any()
        )).thenReturn(List.of(
                summary(values("20", "3", "100", null)),
                summary(values("15", "4", "250", "5"))
        ));

        NutritionValues result = service.sumForDate(
                "user-id",
                LocalDate.of(2026, 8, 16)
        );

        ArgumentCaptor<Collection<MealStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(nutritionSummaryRepository).findDailySummaries(
                eq("user-id"),
                statuses.capture(),
                start.capture(),
                end.capture()
        );
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(MealStatus.CONFIRMED, MealStatus.COMPLETED);
        assertThat(start.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 15, 15, 0));
        assertThat(end.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 16, 15, 0));
        assertThat(result.getProteinG()).isEqualByComparingTo("35");
        assertThat(result.getFiberG()).isEqualByComparingTo("7");
        assertThat(result.getCalciumMg()).isEqualByComparingTo("350");
        assertThat(result.getIronMg()).isEqualByComparingTo("5");
        assertThat(result.getVitaminCMg()).isEqualByComparingTo("0");
    }

    private MealNutritionSummary summary(NutritionValues nutrition) {
        return MealNutritionSummary.builder()
                .nutrition(nutrition)
                .basis(NutritionBasis.USER_CONFIRMED)
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    private NutritionValues values(String protein, String fiber, String calcium, String iron) {
        return NutritionValues.builder()
                .proteinG(decimal(protein))
                .fiberG(decimal(fiber))
                .calciumMg(decimal(calcium))
                .ironMg(decimal(iron))
                .build();
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
