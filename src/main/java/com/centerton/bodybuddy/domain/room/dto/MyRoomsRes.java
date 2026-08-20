package com.centerton.bodybuddy.domain.room.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class MyRoomsRes {
    private List<RoomInfo> rooms;

    @Getter
    @Builder
    public static class RoomInfo {
        private String roomId;
        private String roomName;
        private String coverImageUrl;
        private LocalDateTime joinedAt;
    }
}