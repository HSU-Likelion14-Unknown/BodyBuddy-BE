package com.centerton.bodybuddy.domain.recommendation.dto;

import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class NutrientCoverageRes {
    private TargetNutrient nutrient;
    private BigDecimal coveragePercent;
}
