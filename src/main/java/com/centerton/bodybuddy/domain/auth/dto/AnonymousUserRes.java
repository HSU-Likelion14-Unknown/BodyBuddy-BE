package com.centerton.bodybuddy.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AnonymousUserRes {
    private String userId;
    private String accessKey;
    private LocalDateTime onboardingCompletedAt;
}