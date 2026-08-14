package com.centerton.bodybuddy.domain.room.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.room.entity.RoomMember;
import com.centerton.bodybuddy.domain.room.exception.RoomErrorCode;
import com.centerton.bodybuddy.domain.room.repository.RoomMemberRepository;
import com.centerton.bodybuddy.domain.room.repository.RoomRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomLeaveService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public void leaveRoom(String authorization, String roomId) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        if (!roomRepository.existsById(roomId)) {
            throw new BaseException(RoomErrorCode.ROOM_NOT_FOUND);
        }

        RoomMember member = roomMemberRepository.findByRoomIdAndUserId(roomId, user.getUserId())
                .orElseThrow(() -> new BaseException(RoomErrorCode.NOT_ROOM_MEMBER));

        roomMemberRepository.delete(member);
    }
}