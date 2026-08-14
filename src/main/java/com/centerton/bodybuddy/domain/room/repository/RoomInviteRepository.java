package com.centerton.bodybuddy.domain.room.repository;

import com.centerton.bodybuddy.domain.room.entity.RoomInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomInviteRepository extends JpaRepository<RoomInvite, String> {
    Optional<RoomInvite> findByCode(String code);
}