package com.centerton.bodybuddy.domain.room.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.room.dto.RoomCoverUpdateReq;
import com.centerton.bodybuddy.domain.room.dto.RoomCoverUpdateRes;
import com.centerton.bodybuddy.domain.room.entity.Room;
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
public class RoomCoverService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoomCoverUpdateRes updateCover(String authorization, String roomId, RoomCoverUpdateReq req) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BaseException(RoomErrorCode.ROOM_NOT_FOUND));

        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId, user.getUserId())) {
            throw new BaseException(RoomErrorCode.NOT_ROOM_MEMBER);
        }

        room.updateCoverImage(req.getCoverImageUrl());

        return RoomCoverUpdateRes.builder()
                .roomId(room.getRoomId())
                .coverImageUrl(room.getCoverImageUrl())
                .build();
    }
}