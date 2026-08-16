package com.centerton.bodybuddy.domain.analysis.client;

import com.centerton.bodybuddy.domain.meal.entity.MealInputType;

public record FoodRecognitionInput(
        MealInputType inputType,
        String text,
        byte[] imageBytes,
        String imageMediaType
) {

    public static FoodRecognitionInput text(String text) {
        return new FoodRecognitionInput(MealInputType.TEXT, text, null, null);
    }

    public static FoodRecognitionInput image(byte[] imageBytes, String imageMediaType) {
        return new FoodRecognitionInput(MealInputType.IMAGE, null, imageBytes, imageMediaType);
    }
}
