package com.centerton.bodybuddy.domain.user.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PreferenceReq {

    @NotEmpty(message = "알레르기 정보를 입력해주세요.")
    private List<String> allergyCodes;

    @NotEmpty(message = "비선호 음식 정보를 입력해주세요.")
    private List<String> dislikedFoods;
}