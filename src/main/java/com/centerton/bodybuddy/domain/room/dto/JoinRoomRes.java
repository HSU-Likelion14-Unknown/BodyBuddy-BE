package com.centerton.bodybuddy.domain.room.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class JoinRoomRes {
    private String roomId;
    private String roomName;
    private LocalDateTime joinedAt;
}