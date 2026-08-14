package com.centerton.bodybuddy.domain.room.repository;

import com.centerton.bodybuddy.domain.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, String> {
    boolean existsByRoomIdAndUserId(String roomId, String userId);
    List<RoomMember> findByRoomId(String roomId);
    List<RoomMember> findByUserId(String userId);
    Optional<RoomMember> findByRoomIdAndUserId(String roomId, String userId);
}