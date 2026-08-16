package com.centerton.bodybuddy.domain.recommendation.model;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;

import java.math.BigDecimal;
import java.util.function.Function;

public enum TargetNutrient {
    PROTEIN(NutritionValues::getProteinG),
    FIBER(NutritionValues::getFiberG),
    CALCIUM(NutritionValues::getCalciumMg),
    IRON(NutritionValues::getIronMg),
    POTASSIUM(NutritionValues::getPotassiumMg),
    VITAMIN_A(NutritionValues::getVitaminAMcgRae),
    VITAMIN_C(NutritionValues::getVitaminCMg);

    private final Function<NutritionValues, BigDecimal> extractor;

    TargetNutrient(Function<NutritionValues, BigDecimal> extractor) {
        this.extractor = extractor;
    }

    public BigDecimal amountFrom(NutritionValues values) {
        if (values == null) {
            return null;
        }
        return extractor.apply(values);
    }
}
