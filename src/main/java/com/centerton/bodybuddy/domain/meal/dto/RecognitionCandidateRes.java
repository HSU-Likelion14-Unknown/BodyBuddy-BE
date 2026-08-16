package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.analysis.entity.RecognizedFood;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RecognitionCandidateRes {
    private String candidateId;
    private String aiFoodName;
    private String foodId;
    private BigDecimal confidence;
    private int candidateOrder;
    private RecognitionSelectionStatus selectionStatus;

    public static RecognitionCandidateRes from(RecognizedFood food, String candidateId, int order) {
        return RecognitionCandidateRes.builder()
                .candidateId(candidateId)
                .aiFoodName(food.getFoodName())
                .foodId(food.getFoodId())
                .confidence(food.getConfidence())
                .candidateOrder(order)
                .selectionStatus(RecognitionSelectionStatus.PENDING)
                .build();
    }
}
