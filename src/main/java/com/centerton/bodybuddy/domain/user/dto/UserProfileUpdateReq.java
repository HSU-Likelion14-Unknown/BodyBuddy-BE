package com.centerton.bodybuddy.domain.user.dto;

import com.centerton.bodybuddy.domain.user.entity.Gender;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class UserProfileUpdateReq {
    private String nickname;
    private Integer birthYear;
    private Gender gender;
    private List<String> allergyCodes;
    private List<String> dislikedFoods;
    private Boolean shareToRoom;
}