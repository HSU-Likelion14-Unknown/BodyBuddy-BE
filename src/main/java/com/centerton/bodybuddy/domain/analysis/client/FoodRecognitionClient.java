package com.centerton.bodybuddy.domain.analysis.client;

public interface FoodRecognitionClient {
    FoodRecognitionResponse recognize(FoodRecognitionInput input);
}
