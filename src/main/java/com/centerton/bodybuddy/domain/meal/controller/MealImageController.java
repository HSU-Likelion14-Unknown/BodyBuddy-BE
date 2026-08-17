package com.centerton.bodybuddy.domain.calendar.controller;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.meal.storage.MealImageStorage;
import com.centerton.bodybuddy.domain.meal.storage.StoredMealImage;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meals/images")
@RequiredArgsConstructor
public class MealImageController {

    private final MealImageStorage mealImageStorage;
    private final UserRepository userRepository;

    @GetMapping("/**")
    public ResponseEntity<byte[]> getMealImage(
            @RequestHeader("Authorization") String authorization,
            HttpServletRequest request
    ) {
        AuthValidator.validateAndGetUser(authorization, userRepository);

        String objectKey = extractObjectKey(request);
        StoredMealImage image = mealImageStorage.load(objectKey);

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .body(image.bytes());
    }

    private String extractObjectKey(HttpServletRequest request) {
        String path = request.getRequestURI();
        String prefix = "/api/v1/meals/images/";
        return path.substring(path.indexOf(prefix) + prefix.length());
    }
}