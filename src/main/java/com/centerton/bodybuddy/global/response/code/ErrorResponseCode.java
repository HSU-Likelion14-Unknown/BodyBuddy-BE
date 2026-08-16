package com.centerton.bodybuddy.global.response.code;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.centerton.bodybuddy.global.constant.StaticValue.*;

@Getter
@AllArgsConstructor
public enum ErrorResponseCode implements BaseResponseCode {
    BAD_REQUEST_ERROR("BAD_REQUEST", BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_HTTP_MESSAGE_BODY("BAD_REQUEST", BAD_REQUEST, "요청 본문 형식이 올바르지 않습니다."),
    INVALID_HTTP_MESSAGE_PARAMETER("BAD_REQUEST", BAD_REQUEST, "요청 파라미터 형식이 올바르지 않습니다."),
    UNAUTHORIZED_REQUEST("UNAUTHORIZED", UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED_REQUEST("FORBIDDEN", FORBIDDEN, "요청한 리소스에 접근할 권한이 없습니다."),
    NOT_FOUND_ENDPOINT("RESOURCE_NOT_FOUND", RESOURCE_NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    MEAL_NOT_FOUND("RESOURCE_NOT_FOUND", RESOURCE_NOT_FOUND, "식사를 찾을 수 없습니다."),
    UNSUPPORTED_HTTP_METHOD("METHOD_NOT_ALLOWED", METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_CONFLICT("STATE_CONFLICT", STATE_CONFLICT, "현재 상태에서는 요청을 처리할 수 없습니다."),
    IDEMPOTENCY_KEY_REUSED("IDEMPOTENCY_KEY_REUSED", STATE_CONFLICT, "같은 Idempotency-Key가 다른 요청에 사용되었습니다."),
    ONBOARDING_NOT_COMPLETED("ONBOARDING_NOT_COMPLETED", STATE_CONFLICT, "온보딩을 먼저 완료해 주세요."),
    INVALID_MEAL_STATUS("MEAL_CONFIRMATION_CONFLICT", STATE_CONFLICT, "이미 확정되었거나 현재 상태에서 확정할 수 없습니다."),
    RECOGNITION_NOT_READY("RECOGNITION_NOT_READY", STATE_CONFLICT, "음식 인식 결과가 아직 준비되지 않았습니다."),
    RECOGNITION_RETRY_CONFLICT("RECOGNITION_RETRY_CONFLICT", STATE_CONFLICT, "현재 상태에서는 음식 재인식을 요청할 수 없습니다."),
    MEAL_COMPLETION_CONFLICT("MEAL_COMPLETION_CONFLICT", STATE_CONFLICT, "현재 상태에서는 식사 기록을 완료할 수 없습니다."),
    MEAL_VERSION_CONFLICT("MEAL_VERSION_CONFLICT", STATE_CONFLICT, "식사 기록이 변경되었습니다. 최신 정보를 다시 조회해 주세요."),
    RECOMMENDATION_NOT_FOUND("RECOMMENDATION_NOT_FOUND", RESOURCE_NOT_FOUND, "추천 결과를 찾을 수 없습니다."),
    RECOMMENDATION_CREATION_CONFLICT("RECOMMENDATION_CREATION_CONFLICT", STATE_CONFLICT, "현재 식사에는 추천을 생성할 수 없습니다."),
    RECOMMENDATION_ALREADY_EXISTS("RECOMMENDATION_ALREADY_EXISTS", STATE_CONFLICT, "이 식사에는 이미 추천이 생성되었습니다."),
    RECOMMENDATION_DECISION_CONFLICT("RECOMMENDATION_DECISION_CONFLICT", STATE_CONFLICT, "이미 결정되었거나 결정할 수 없는 추천입니다."),
    RECOMMENDATION_DECISION_INVALID("RECOMMENDATION_DECISION_INVALID", VALIDATION_ERROR, "추천 결정값 또는 원재료를 확인해 주세요."),
    REQUEST_ENTITY_TOO_LARGE("PAYLOAD_TOO_LARGE", PAYLOAD_TOO_LARGE, "요청 크기가 허용 범위를 초과했습니다."),
    INVALID_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입입니다."),
    INVALID_INPUT_VALUE("VALIDATION_ERROR", VALIDATION_ERROR, "요청값을 확인해 주세요."),
    MEAL_ITEMS_INVALID("MEAL_ITEMS_INVALID", VALIDATION_ERROR, "음식 또는 섭취량을 확인해 주세요."),
    KDRI_PROFILE_REQUIRED("KDRI_PROFILE_REQUIRED", VALIDATION_ERROR, "영양 기준 계산을 위한 출생연도와 성별 정보가 필요합니다."),
    TOO_MANY_REQUESTS("RATE_LIMITED", RATE_LIMITED, "요청 빈도 제한을 초과했습니다."),
    SERVER_ERROR("INTERNAL_SERVER_ERROR", INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    AI_BAD_RESPONSE("AI_RESPONSE_INVALID", AI_RESPONSE_INVALID, "AI 응답 형식이 올바르지 않습니다."),
    AI_SERVICE_UNAVAILABLE("AI_UNAVAILABLE", AI_UNAVAILABLE, "AI 서비스를 일시적으로 사용할 수 없습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
