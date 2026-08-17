package com.centerton.bodybuddy.domain.recommendation.dto;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class NutritionGapRes {
    private BigDecimal proteinG;
    private BigDecimal fiberG;
    private BigDecimal calciumMg;
    private BigDecimal ironMg;
    private BigDecimal potassiumMg;
    private BigDecimal vitaminAMcgRae;
    private BigDecimal vitaminCMg;

    public static NutritionGapRes from(NutritionValues values) {
        return NutritionGapRes.builder()
                .proteinG(zeroIfNull(values.getProteinG()))
                .fiberG(zeroIfNull(values.getFiberG()))
                .calciumMg(zeroIfNull(values.getCalciumMg()))
                .ironMg(zeroIfNull(values.getIronMg()))
                .potassiumMg(zeroIfNull(values.getPotassiumMg()))
                .vitaminAMcgRae(zeroIfNull(values.getVitaminAMcgRae()))
                .vitaminCMg(zeroIfNull(values.getVitaminCMg()))
                .build();
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
