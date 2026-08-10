package com.centerton.bodybuddy.domain.auth.controller;

import com.centerton.bodybuddy.domain.auth.service.AuthService;
import com.centerton.bodybuddy.domain.auth.dto.AnonymousUserRes;
import com.centerton.bodybuddy.global.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/anonymous")
    public ResponseEntity<SuccessResponse<AnonymousUserRes>> createAnonymousUser(
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        AnonymousUserRes response = authService.createAnonymousUser(idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }
}