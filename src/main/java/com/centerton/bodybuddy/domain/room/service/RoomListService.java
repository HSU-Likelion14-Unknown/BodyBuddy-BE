package com.centerton.bodybuddy.domain.room.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.room.dto.MyRoomsRes;
import com.centerton.bodybuddy.domain.room.entity.Room;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomListService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MyRoomsRes getMyRooms(String authorization) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        List<RoomMember> myMemberships = roomMemberRepository.findByUserId(user.getUserId());

        List<String> roomIds = myMemberships.stream()
                .map(RoomMember::getRoomId)
                .toList();

        Map<String, Room> roomMap = roomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(Room::getRoomId, Function.identity()));

        List<MyRoomsRes.RoomInfo> roomInfos = myMemberships.stream()
                .map(m -> MyRoomsRes.RoomInfo.builder()
                        .roomId(m.getRoomId())
                        .roomName(roomMap.get(m.getRoomId()).getRoomName())
                        .joinedAt(m.getCreatedAt())
                        .build())
                .toList();

        return MyRoomsRes.builder()
                .rooms(roomInfos)
                .build();
    }
}