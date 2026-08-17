package com.centerton.bodybuddy.domain.meal.storage;

public record StoredMealImage(
        byte[] bytes,
        String mediaType
) {
}
