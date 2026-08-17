package com.centerton.bodybuddy.domain.analysis.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "bodybuddy.food-recognition.provider",
        havingValue = "fake",
        matchIfMissing = true
)
public class FakeFoodRecognitionClient implements FoodRecognitionClient {

    @Override
    public FoodRecognitionResponse recognize(FoodRecognitionInput input) {
        String foodName = input.inputType() == com.centerton.bodybuddy.domain.meal.entity.MealInputType.TEXT
                ? input.text().trim()
                : "김치찌개";
        return new FoodRecognitionResponse(
                FoodRecognitionResultType.FOOD,
                List.of(new FoodRecognitionCandidate(foodName, new BigDecimal("0.8500"))),
                "FAKE",
                "fake-food-recognition-v1",
                "fake-v1",
                UUID.randomUUID().toString(),
                null,
                null
        );
    }
}
