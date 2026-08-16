package com.centerton.bodybuddy.domain.user.dto;

import com.centerton.bodybuddy.domain.user.entity.Gender;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class UserInfoRes {
    private String userId;
    private String nickname;
    private Integer birthYear;
    private Gender gender;
    private List<String> allergyCodes;
    private List<String> dislikedFoods;
    private Boolean shareToRoom;
    private String profileImageUrl;
    private LocalDateTime onboardingCompletedAt;
}