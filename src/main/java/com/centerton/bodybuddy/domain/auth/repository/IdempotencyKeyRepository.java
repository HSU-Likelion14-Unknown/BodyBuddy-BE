package com.centerton.bodybuddy.domain.auth.repository;

import com.centerton.bodybuddy.domain.auth.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO idempotency_keys
                (idempotency_key, user_id, operation, request_fingerprint,
                 resource_id, created_at, updated_at)
            VALUES
                (:key, :userId, :operation, :fingerprint,
                 NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """, nativeQuery = true)
    int reserve(
            @Param("key") String key,
            @Param("userId") String userId,
            @Param("operation") String operation,
            @Param("fingerprint") String fingerprint
    );

    @Modifying
    @Query("""
            update IdempotencyKey record
            set record.resourceId = :resourceId
            where record.idempotencyKey = :key
              and record.resourceId is null
            """)
    int completeReservation(
            @Param("key") String key,
            @Param("resourceId") String resourceId
    );
}
