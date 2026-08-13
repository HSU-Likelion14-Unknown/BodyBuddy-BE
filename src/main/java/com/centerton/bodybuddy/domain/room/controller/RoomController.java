package com.centerton.bodybuddy.domain.room.controller;

import com.centerton.bodybuddy.domain.room.dto.RoomCreateReq;
import com.centerton.bodybuddy.domain.room.dto.RoomCreateRes;
import com.centerton.bodybuddy.domain.room.service.RoomService;
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

    @PostMapping
    public ResponseEntity<SuccessResponse<RoomCreateRes>> createRoom(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody RoomCreateReq req
    ) {
        RoomCreateRes response = roomService.createRoom(authorization, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }
}