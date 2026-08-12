package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.analysis.entity.RecognizedFood;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RecognizedItemRes {
    private String foodId;
    private String foodName;
    private BigDecimal confidence;

    public static RecognizedItemRes from(RecognizedFood food) {
        return RecognizedItemRes.builder()
                .foodId(food.getFoodId())
                .foodName(food.getFoodName())
                .confidence(food.getConfidence())
                .build();
    }
}
