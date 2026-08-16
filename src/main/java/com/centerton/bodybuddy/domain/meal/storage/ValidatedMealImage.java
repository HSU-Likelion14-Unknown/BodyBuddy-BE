package com.centerton.bodybuddy.domain.meal.storage;

public record ValidatedMealImage(
        byte[] bytes,
        String mediaType,
        String extension,
        String sha256
) {
}
