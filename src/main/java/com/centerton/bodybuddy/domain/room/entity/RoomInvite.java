package com.centerton.bodybuddy.domain.room.entity;

import com.centerton.bodybuddy.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_invites")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomInvite extends BaseEntity {

    @Id
    @Column(name = "invite_id", length = 36)
    private String inviteId;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "room_id", nullable = false, length = 36)
    private String roomId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}