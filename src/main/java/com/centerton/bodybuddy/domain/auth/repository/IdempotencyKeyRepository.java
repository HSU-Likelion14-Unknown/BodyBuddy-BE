package com.centerton.bodybuddy.domain.auth.repository;

import com.centerton.bodybuddy.domain.auth.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
    boolean existsByIdempotencyKey(String idempotencyKey);
}