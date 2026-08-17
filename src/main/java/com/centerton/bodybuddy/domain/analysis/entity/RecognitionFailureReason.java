package com.centerton.bodybuddy.domain.analysis.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum RecognitionFailureReason {
    NO_FOOD("NO_FOOD", "사진이나 입력에서 음식을 찾지 못했습니다."),
    LOW_CONFIDENCE("LOW_CONFIDENCE", "음식 인식 결과의 신뢰도가 너무 낮습니다."),
    AI_UNAVAILABLE("AI_UNAVAILABLE", "AI 서비스를 일시적으로 사용할 수 없습니다."),
    INVALID_RESPONSE("AI_RESPONSE_INVALID", "음식 인식 결과를 처리하지 못했습니다."),
    UNKNOWN("UNKNOWN", "음식 인식 결과를 불러오지 못했습니다.");

    private final String errorCode;
    private final String message;

    public static RecognitionFailureReason fromErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(reason -> reason.errorCode.equals(errorCode))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
