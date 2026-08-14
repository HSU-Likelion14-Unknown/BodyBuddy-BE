package com.centerton.bodybuddy.domain.room.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.room.dto.*;
import com.centerton.bodybuddy.domain.room.entity.*;
import com.centerton.bodybuddy.domain.room.exception.RoomErrorCode;
import com.centerton.bodybuddy.domain.room.repository.*;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomJoinService {

    private final RoomRepository roomRepository;
    private final RoomInviteRepository roomInviteRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public JoinRoomRes joinRoom(String authorization, JoinRoomReq req) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        RoomInvite invite = roomInviteRepository.findByCode(req.getCode())
                .orElseThrow(() -> new BaseException(RoomErrorCode.INVITE_NOT_FOUND));

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BaseException(RoomErrorCode.INVITE_NOT_FOUND);
        }

        Room room = roomRepository.findById(invite.getRoomId())
                .orElseThrow(() -> new BaseException(RoomErrorCode.ROOM_NOT_FOUND));

        if (roomMemberRepository.existsByRoomIdAndUserId(room.getRoomId(), user.getUserId())) {
            throw new BaseException(RoomErrorCode.ALREADY_JOINED_ROOM);
        }

        RoomMember member = RoomMember.builder()
                .memberId(UUID.randomUUID().toString())
                .roomId(room.getRoomId())
                .userId(user.getUserId())
                .build();
        roomMemberRepository.save(member);

        return JoinRoomRes.builder()
                .roomId(room.getRoomId())
                .roomName(room.getRoomName())
                .joinedAt(member.getCreatedAt())
                .build();
    }
}