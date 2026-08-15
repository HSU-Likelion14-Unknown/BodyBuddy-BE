package com.centerton.bodybuddy.domain.room.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JoinRoomReq {

    @NotBlank(message = "초대 코드를 확인해주세요.")
    private String code;
}