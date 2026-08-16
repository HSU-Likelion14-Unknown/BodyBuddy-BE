package com.centerton.bodybuddy.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProfileImageUpdateRes {
    private String userId;
    private String profileImageUrl;
}