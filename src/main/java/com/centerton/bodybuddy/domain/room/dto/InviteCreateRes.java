package com.centerton.bodybuddy.domain.room.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InviteCreateRes {
    private String inviteId;
    private String code;
    private LocalDateTime expiresAt;
}