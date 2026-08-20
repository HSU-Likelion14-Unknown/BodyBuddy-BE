package com.centerton.bodybuddy.domain.calendar.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.calendar.dto.CalendarMealImageUpdateRes;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import com.centerton.bodybuddy.global.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CalendarMealImageService {

    private final UserRepository userRepository;
    private final MealRepository mealRepository;
    private final ImageStorage imageStorage;

    @Transactional
    public CalendarMealImageUpdateRes updateImage(
            String authorization,
            String mealId,
            MultipartFile image
    ) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);
        Meal meal = mealRepository.findByMealIdAndUserUserId(mealId, user.getUserId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MEAL_NOT_FOUND));

        String photoUrl = imageStorage.store(image);
        meal.updatePhoto(photoUrl);

        return CalendarMealImageUpdateRes.builder()
                .mealId(meal.getMealId())
                .photoUrl(meal.getPhotoObjectKey())
                .build();
    }
}
