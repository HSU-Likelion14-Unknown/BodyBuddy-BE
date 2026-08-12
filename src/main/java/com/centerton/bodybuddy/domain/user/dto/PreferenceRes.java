package com.centerton.bodybuddy.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PreferenceRes {
    private List<String> allergyCodes;
    private List<String> dislikedFoods;
}