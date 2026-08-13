package com.centerton.bodybuddy.domain.room.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.room.dto.RoomCreateReq;
import com.centerton.bodybuddy.domain.room.dto.RoomCreateRes;
import com.centerton.bodybuddy.domain.room.entity.Room;
import com.centerton.bodybuddy.domain.room.repository.RoomRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoomCreateRes createRoom(String authorization, RoomCreateReq req) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        Room room = Room.builder()
                .roomId(UUID.randomUUID().toString())
                .roomName(req.getRoomName())
                .userId(user.getUserId())
                .build();
        roomRepository.save(room);

        return RoomCreateRes.builder()
                .roomId(room.getRoomId())
                .roomName(room.getRoomName())
                .createdAt(room.getCreatedAt())
                .build();
    }
}