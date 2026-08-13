package com.centerton.bodybuddy.domain.room.entity;

import com.centerton.bodybuddy.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room extends BaseEntity {

    @Id
    @Column(name = "room_id", length = 36)
    private String roomId;

    @Column(name = "room_name", length = 100, nullable = false)
    private String roomName;

    @Column(name = "user_id", nullable = false, unique = true, length = 36)
    private String userId;
}