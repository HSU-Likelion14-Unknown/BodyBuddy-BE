package com.centerton.bodybuddy.domain.recommendation.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "bodybuddy.recommendation")
public class RecommendationPolicyProperties {

    @Min(2)
    @Max(3)
    private int ingredientCount = 2;

    @Min(1)
    private int maxRefreshCount = 3;

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal minimumTargetCoveragePercent = new BigDecimal("20.0");

    public BigDecimal minimumTargetCoverageRatio() {
        return minimumTargetCoveragePercent.movePointLeft(2);
    }
}
