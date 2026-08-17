package com.centerton.bodybuddy.domain.auth.service;

import com.centerton.bodybuddy.domain.auth.entity.IdempotencyKey;
import com.centerton.bodybuddy.domain.auth.repository.IdempotencyKeyRepository;
import com.centerton.bodybuddy.domain.auth.util.AccessKeyGenerator;
import com.centerton.bodybuddy.domain.auth.dto.AnonymousUserRes;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import com.centerton.bodybuddy.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional
    public AnonymousUserRes createAnonymousUser(String idempotencyKey) {

        if (idempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.warn("Idempotency-Key 재사용 감지 - idempotencyKey: {}", idempotencyKey);
            throw new BaseException(ErrorResponseCode.IDEMPOTENCY_KEY_REUSED);
        }

        String rawAccessKey = AccessKeyGenerator.generateRawKey();
        String accessKeyHash = AccessKeyGenerator.hash(rawAccessKey);

        User user = User.builder()
                .userId(UUID.randomUUID().toString())
                .accessKeyHash(accessKeyHash)
                .build();
        userRepository.save(user);

        IdempotencyKey record = IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .userId(user.getUserId())
                .build();
        idempotencyKeyRepository.save(record);

        log.info("익명 사용자 생성 완료 - userId: {}", user.getUserId());

        return AnonymousUserRes.builder()
                .userId(user.getUserId())
                .accessKey(rawAccessKey)
                .onboardingCompletedAt(user.getOnboardingCompletedAt())
                .build();
    }
}
