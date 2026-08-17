package com.centerton.bodybuddy.domain.analysis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognizedFood {
    private String foodId;
    private String foodName;
    private BigDecimal confidence;
}
