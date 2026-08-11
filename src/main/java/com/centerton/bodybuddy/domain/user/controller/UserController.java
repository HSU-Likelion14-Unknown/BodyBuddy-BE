package com.centerton.bodybuddy.domain.user.controller;

import com.centerton.bodybuddy.domain.user.dto.*;
import com.centerton.bodybuddy.domain.user.service.UserService;
import com.centerton.bodybuddy.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/me/onboarding")
    public ResponseEntity<SuccessResponse<OnboardingRes>> saveOnboarding(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody OnboardingReq req
    ) {
        OnboardingRes response = userService.saveOnboarding(authorization, req);
        return ResponseEntity.status(HttpStatus.OK).body(SuccessResponse.from(response));
    }

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<UserInfoRes>> getMyInfo(
            @RequestHeader("Authorization") String authorization
    ) {
        UserInfoRes response = userService.getMyInfo(authorization);
        return ResponseEntity.status(HttpStatus.OK).body(SuccessResponse.from(response));
    }
}