package com.centerton.bodybuddy.domain.room.exception;

import com.centerton.bodybuddy.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.centerton.bodybuddy.global.constant.StaticValue.*;

@Getter
@AllArgsConstructor
public enum RoomErrorCode implements BaseResponseCode {
    ROOM_NOT_FOUND("ROOM_404_1", RESOURCE_NOT_FOUND, "방을 찾을 수 없습니다."),
    ROOM_ACCESS_DENIED("ROOM_403_1", FORBIDDEN, "방장 또는 방 멤버만 접근할 수 있습니다."),
    INVITE_NOT_FOUND("ROOM_404_2", RESOURCE_NOT_FOUND, "초대 코드를 찾을 수 없거나 만료되었습니다."),
    ALREADY_JOINED_ROOM("ROOM_409_1", STATE_CONFLICT, "이미 참여한 방입니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}