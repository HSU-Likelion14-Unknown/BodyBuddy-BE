package com.centerton.bodybuddy.domain.room.entity;

import com.centerton.bodybuddy.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_members")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMember extends BaseEntity {

    @Id
    @Column(name = "member_id", length = 36)
    private String memberId;

    @Column(name = "room_id", nullable = false, length = 36)
    private String roomId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
}