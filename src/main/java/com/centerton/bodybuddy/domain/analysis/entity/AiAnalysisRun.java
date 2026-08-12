package com.centerton.bodybuddy.domain.analysis.entity;

import com.centerton.bodybuddy.domain.meal.entity.Meal;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_analysis_runs", indexes =
        @Index(name = "idx_ai_runs_meal_started", columnList = "meal_id,started_at"))
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AiAnalysisRun {

    @Id
    @Column(name = "analysis_run_id", length = 36)
    private String analysisRunId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 24)
    private AnalysisRunType runType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 40)
    private String promptVersion;

    @Column(name = "provider_response_id", length = 120)
    private String providerResponseId;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_response", columnDefinition = "json")
    private RecognitionResult normalizedResponse;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static AiAnalysisRun pending(Meal meal, AnalysisRunType runType, String requestFingerprint) {
        return AiAnalysisRun.builder()
                .analysisRunId(UUID.randomUUID().toString())
                .meal(meal)
                .runType(runType)
                .status(AnalysisStatus.PENDING)
                .provider("OPENAI")
                .model("NOT_CONFIGURED")
                .promptVersion("NOT_CONFIGURED")
                .requestFingerprint(requestFingerprint)
                .attemptNo(1)
                .startedAt(LocalDateTime.now())
                .build();
    }
}
