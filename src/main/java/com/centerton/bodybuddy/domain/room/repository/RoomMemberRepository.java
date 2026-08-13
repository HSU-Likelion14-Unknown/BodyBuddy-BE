package com.centerton.bodybuddy.domain.room.repository;

import com.centerton.bodybuddy.domain.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, String> {
    boolean existsByRoomIdAndUserId(String roomId, String userId);
}