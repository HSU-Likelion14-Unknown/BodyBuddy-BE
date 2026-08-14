package com.centerton.bodybuddy.domain.room.controller;

import com.centerton.bodybuddy.domain.room.dto.*;
import com.centerton.bodybuddy.domain.room.service.*;
import com.centerton.bodybuddy.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomInviteService roomInviteService;
    private final RoomJoinService roomJoinService;
    private final RoomMemberService roomMemberService;
    private final RoomListService roomListService;
    private final RoomLeaveService roomLeaveService;

    @PostMapping
    public ResponseEntity<SuccessResponse<RoomCreateRes>> createRoom(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody RoomCreateReq req
    ) {
        RoomCreateRes response = roomService.createRoom(authorization, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }

    @PostMapping("/{roomId}/invites")
    public ResponseEntity<SuccessResponse<InviteCreateRes>> createInvite(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String roomId
    ) {
        InviteCreateRes response = roomInviteService.createInvite(authorization, roomId);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }

    @PostMapping("/join")
    public ResponseEntity<SuccessResponse<JoinRoomRes>> joinRoom(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody JoinRoomReq req
    ) {
        JoinRoomRes response = roomJoinService.joinRoom(authorization, req);
        return ResponseEntity.status(HttpStatus.OK).body(SuccessResponse.from(response));
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<SuccessResponse<RoomMembersRes>> getMembers(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String roomId
    ) {
        RoomMembersRes response = roomMemberService.getMembers(authorization, roomId);
        return ResponseEntity.status(HttpStatus.OK).body(SuccessResponse.from(response));
    }

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<MyRoomsRes>> getMyRooms(
            @RequestHeader("Authorization") String authorization
    ) {
        MyRoomsRes response = roomListService.getMyRooms(authorization);
        return ResponseEntity.status(HttpStatus.OK).body(SuccessResponse.from(response));
    }

    @DeleteMapping("/{roomId}/members/me")
    public ResponseEntity<SuccessResponse<?>> leaveRoom(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String roomId
    ) {
        roomLeaveService.leaveRoom(authorization, roomId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(SuccessResponse.empty());
    }
}