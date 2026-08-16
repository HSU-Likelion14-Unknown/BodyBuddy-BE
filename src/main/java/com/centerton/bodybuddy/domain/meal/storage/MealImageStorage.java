package com.centerton.bodybuddy.domain.meal.storage;

import org.springframework.web.multipart.MultipartFile;

public interface MealImageStorage {
    ValidatedMealImage validate(MultipartFile file);

    String store(ValidatedMealImage image);

    StoredMealImage load(String objectKey);
}
