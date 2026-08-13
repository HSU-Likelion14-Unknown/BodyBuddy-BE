package com.centerton.bodybuddy.domain.room.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RoomCreateReq {

    @NotBlank(message = "방 이름을 확인해주세요.")
    private String roomName;
}