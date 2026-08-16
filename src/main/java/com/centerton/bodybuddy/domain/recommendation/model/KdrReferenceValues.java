package com.centerton.bodybuddy.domain.recommendation.model;

import java.math.BigDecimal;

public record KdrReferenceValues(
        BigDecimal proteinG,
        BigDecimal fiberG,
        BigDecimal calciumMg,
        BigDecimal ironMg,
        BigDecimal potassiumMg,
        BigDecimal vitaminAMcgRae,
        BigDecimal vitaminCMg
) {

    public BigDecimal amountOf(TargetNutrient nutrient) {
        return switch (nutrient) {
            case PROTEIN -> proteinG;
            case FIBER -> fiberG;
            case CALCIUM -> calciumMg;
            case IRON -> ironMg;
            case POTASSIUM -> potassiumMg;
            case VITAMIN_A -> vitaminAMcgRae;
            case VITAMIN_C -> vitaminCMg;
        };
    }

    public static KdrReferenceValues minimum(KdrReferenceValues first,
                                             KdrReferenceValues second) {
        return new KdrReferenceValues(
                first.proteinG.min(second.proteinG),
                first.fiberG.min(second.fiberG),
                first.calciumMg.min(second.calciumMg),
                first.ironMg.min(second.ironMg),
                first.potassiumMg.min(second.potassiumMg),
                first.vitaminAMcgRae.min(second.vitaminAMcgRae),
                first.vitaminCMg.min(second.vitaminCMg)
        );
    }
}
