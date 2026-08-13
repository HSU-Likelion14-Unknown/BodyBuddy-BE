package com.centerton.bodybuddy.domain.room.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.room.dto.InviteCreateRes;
import com.centerton.bodybuddy.domain.room.entity.Room;
import com.centerton.bodybuddy.domain.room.entity.RoomInvite;
import com.centerton.bodybuddy.domain.room.repository.RoomInviteRepository;
import com.centerton.bodybuddy.domain.room.repository.RoomRepository;
import com.centerton.bodybuddy.domain.room.util.InviteCodeGenerator;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomInviteService {

    private final RoomRepository roomRepository;
    private final RoomInviteRepository roomInviteRepository;
    private final UserRepository userRepository;

    @Transactional
    public InviteCreateRes createInvite(String authorization, String roomId) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.ROOM_NOT_FOUND));

        if (!room.getUserId().equals(user.getUserId())) {
            throw new BaseException(ErrorResponseCode.ROOM_ACCESS_DENIED);
        }

        RoomInvite invite = RoomInvite.builder()
                .inviteId(UUID.randomUUID().toString())
                .code(InviteCodeGenerator.generate())
                .roomId(room.getRoomId())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        roomInviteRepository.save(invite);

        return InviteCreateRes.builder()
                .inviteId(invite.getInviteId())
                .code(invite.getCode())
                .expiresAt(invite.getExpiresAt())
                .build();
    }
}