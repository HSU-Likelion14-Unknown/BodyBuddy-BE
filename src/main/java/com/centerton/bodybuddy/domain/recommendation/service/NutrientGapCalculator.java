package com.centerton.bodybuddy.domain.recommendation.service;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutrientGap;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

@Component
public class NutrientGapCalculator {

    private static final int RATIO_SCALE = 8;

    public NutritionGapResult calculate(KdrReferenceValues reference,
                                        NutritionValues dailyNutrition) {
        Map<TargetNutrient, NutrientGap> gaps = new EnumMap<>(TargetNutrient.class);
        TargetNutrient target = null;
        BigDecimal largestRatio = BigDecimal.ZERO;

        for (TargetNutrient nutrient : TargetNutrient.values()) {
            BigDecimal targetAmount = reference.amountOf(nutrient);
            BigDecimal consumed = zeroIfNull(nutrient.amountFrom(dailyNutrition));
            BigDecimal gap = targetAmount.subtract(consumed).max(BigDecimal.ZERO);
            BigDecimal ratio = gap.signum() == 0
                    ? BigDecimal.ZERO
                    : gap.divide(targetAmount, RATIO_SCALE, RoundingMode.HALF_UP);
            gaps.put(nutrient, new NutrientGap(targetAmount, consumed, gap, ratio));

            if (ratio.compareTo(largestRatio) > 0) {
                largestRatio = ratio;
                target = nutrient;
            }
        }

        return new NutritionGapResult(reference, dailyNutrition, gaps, target);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
