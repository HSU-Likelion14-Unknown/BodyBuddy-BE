package com.centerton.bodybuddy.domain.user.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.user.dto.OnboardingReq;
import com.centerton.bodybuddy.domain.user.dto.OnboardingRes;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public OnboardingRes saveOnboarding(String authorization, OnboardingReq req) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        user.updateOnboarding(
                req.getNickname(),
                req.getBirthYear(),
                req.getGender(),
                String.join(",", req.getAllergyCodes()),
                String.join(",", req.getDislikedFoods()),
                LocalDateTime.now()
        );

        return OnboardingRes.builder()
                .nickname(user.getNickname())
                .birthYear(user.getBirthYear())
                .gender(user.getGender())
                .allergyCodes(req.getAllergyCodes())
                .dislikedFoods(req.getDislikedFoods())
                .onboardingCompletedAt(user.getOnboardingCompletedAt())
                .build();
    }
}