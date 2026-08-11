package com.centerton.bodybuddy.domain.user.dto;

import com.centerton.bodybuddy.domain.user.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OnboardingReq {

    @NotBlank(message = "닉네임을 입력해주세요.")
    private String nickname;

    @NotNull(message = "출생연도를 입력해주세요.")
    private Integer birthYear;

    @NotNull(message = "성별을 선택해주세요.")
    private Gender gender;

    @NotEmpty(message = "알레르기 정보를 입력해주세요.")
    private List<String> allergyCodes;

    @NotEmpty(message = "비선호 음식 정보를 입력해주세요.")
    private List<String> dislikedFoods;
}