package com.centerton.bodybuddy.domain.user.dto;

import com.centerton.bodybuddy.domain.user.entity.Gender;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OnboardingRes {
    private String nickname;
    private Integer birthYear;
    private Gender gender;
    private List<String> allergyCodes;
    private List<String> dislikedFoods;
    private LocalDateTime onboardingCompletedAt;
}