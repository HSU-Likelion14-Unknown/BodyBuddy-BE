package com.centerton.bodybuddy.domain.meal.dto;

import com.centerton.bodybuddy.domain.analysis.entity.AiAnalysisRun;
import com.centerton.bodybuddy.domain.analysis.entity.RecognitionFailureReason;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecognitionFailureRes {
    private RecognitionFailureReason reason;
    private String message;

    public static RecognitionFailureRes from(AiAnalysisRun run) {
        RecognitionFailureReason reason = RecognitionFailureReason.fromErrorCode(
                run == null ? null : run.getErrorCode()
        );
        return RecognitionFailureRes.builder()
                .reason(reason)
                .message(reason.getMessage())
                .build();
    }
}
