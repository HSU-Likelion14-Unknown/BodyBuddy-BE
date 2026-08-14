package com.centerton.bodybuddy.domain.room.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.room.dto.RoomMembersRes;
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
public class RoomMemberService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public RoomMembersRes getMembers(String authorization, String roomId) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        if (!roomRepository.existsById(roomId)) {
            throw new BaseException(RoomErrorCode.ROOM_NOT_FOUND);
        }

        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId, user.getUserId())) {
            throw new BaseException(RoomErrorCode.NOT_ROOM_MEMBER);
        }

        List<RoomMember> roomMembers = roomMemberRepository.findByRoomId(roomId);

        List<String> userIds = roomMembers.stream()
                .map(RoomMember::getUserId)
                .toList();

        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        List<RoomMembersRes.MemberInfo> memberInfos = roomMembers.stream()
                .map(rm -> RoomMembersRes.MemberInfo.builder()
                        .userId(rm.getUserId())
                        .nickname(userMap.get(rm.getUserId()).getNickname())
                        .joinedAt(rm.getCreatedAt())
                        .build())
                .toList();

        return RoomMembersRes.builder()
                .roomId(roomId)
                .members(memberInfos)
                .build();
    }
}