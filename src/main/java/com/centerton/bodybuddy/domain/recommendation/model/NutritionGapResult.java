package com.centerton.bodybuddy.domain.recommendation.model;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public record NutritionGapResult(
        KdrReferenceValues reference,
        NutritionValues dailyNutrition,
        Map<TargetNutrient, NutrientGap> gaps,
        TargetNutrient targetNutrient
) {

    public NutritionGapResult {
        EnumMap<TargetNutrient, NutrientGap> copied = new EnumMap<>(TargetNutrient.class);
        copied.putAll(gaps);
        gaps = Collections.unmodifiableMap(copied);
    }

    public NutrientGap gapOf(TargetNutrient nutrient) {
        return gaps.get(nutrient);
    }

    public Optional<TargetNutrient> target() {
        return Optional.ofNullable(targetNutrient);
    }
}
