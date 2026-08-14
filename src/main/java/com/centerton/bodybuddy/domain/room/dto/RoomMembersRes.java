package com.centerton.bodybuddy.domain.room.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class RoomMembersRes {
    private String roomId;
    private List<MemberInfo> members;

    @Getter
    @Builder
    public static class MemberInfo {
        private String userId;
        private String nickname;
        private LocalDateTime joinedAt;
    }
}