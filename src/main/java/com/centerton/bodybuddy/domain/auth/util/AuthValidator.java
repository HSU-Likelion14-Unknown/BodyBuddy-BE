package com.centerton.bodybuddy.domain.auth.util;

import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;

public class AuthValidator {

    private AuthValidator() {}

    public static User validateAndGetUser(String authorization, UserRepository userRepository) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BaseException(ErrorResponseCode.UNAUTHORIZED_REQUEST);
        }

        String rawAccessKey = authorization.substring(7);
        String accessKeyHash = AccessKeyGenerator.hash(rawAccessKey);

        return userRepository.findByAccessKeyHash(accessKeyHash)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.UNAUTHORIZED_REQUEST));
    }
}