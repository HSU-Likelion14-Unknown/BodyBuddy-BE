package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NutrientGapCalculatorTest {

    private final NutrientGapCalculator calculator = new NutrientGapCalculator();

    @Test
    void calculatesNonNegativeGapsAndSelectsLargestDeficiencyRatio() {
        NutritionGapResult result = calculator.calculate(
                reference(),
                NutritionValues.builder()
                        .proteinG(new BigDecimal("60"))
                        .fiberG(new BigDecimal("30"))
                        .calciumMg(new BigDecimal("900"))
                        .ironMg(new BigDecimal("2"))
                        .potassiumMg(new BigDecimal("3500"))
                        .vitaminAMcgRae(new BigDecimal("800"))
                        .vitaminCMg(new BigDecimal("120"))
                        .build()
        );

        assertThat(result.gapOf(TargetNutrient.PROTEIN).gapAmount())
                .isEqualByComparingTo("5");
        assertThat(result.gapOf(TargetNutrient.FIBER).gapAmount())
                .isEqualByComparingTo("0");
        assertThat(result.gapOf(TargetNutrient.IRON).gapAmount())
                .isEqualByComparingTo("6");
        assertThat(result.target()).contains(TargetNutrient.IRON);
    }

    @Test
    void returnsNoTargetWhenAllNutrientsMeetReference() {
        NutritionValues sufficient = NutritionValues.builder()
                .proteinG(new BigDecimal("65"))
                .fiberG(new BigDecimal("30"))
                .calciumMg(new BigDecimal("800"))
                .ironMg(new BigDecimal("8"))
                .potassiumMg(new BigDecimal("3500"))
                .vitaminAMcgRae(new BigDecimal("800"))
                .vitaminCMg(new BigDecimal("100"))
                .build();

        assertThat(calculator.calculate(reference(), sufficient).target()).isEmpty();
    }

    @Test
    void usesEnumPriorityWhenDeficiencyRatiosAreEqual() {
        NutritionValues noneConsumed = NutritionValues.builder().build();

        assertThat(calculator.calculate(reference(), noneConsumed).target())
                .contains(TargetNutrient.PROTEIN);
    }

    private KdrReferenceValues reference() {
        return new KdrReferenceValues(
                new BigDecimal("65"),
                new BigDecimal("30"),
                new BigDecimal("800"),
                new BigDecimal("8"),
                new BigDecimal("3500"),
                new BigDecimal("800"),
                new BigDecimal("100")
        );
    }
}
