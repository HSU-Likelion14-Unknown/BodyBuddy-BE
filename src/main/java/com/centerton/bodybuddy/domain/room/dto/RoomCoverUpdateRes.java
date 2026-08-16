package com.centerton.bodybuddy.domain.room.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoomCoverUpdateRes {
    private String roomId;
    private String coverImageUrl;
}