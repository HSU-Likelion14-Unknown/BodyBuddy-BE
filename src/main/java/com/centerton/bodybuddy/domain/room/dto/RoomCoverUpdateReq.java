package com.centerton.bodybuddy.domain.room.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RoomCoverUpdateReq {

    @NotBlank(message = "커버 이미지를 확인해주세요.")
    private String coverImageUrl;
}