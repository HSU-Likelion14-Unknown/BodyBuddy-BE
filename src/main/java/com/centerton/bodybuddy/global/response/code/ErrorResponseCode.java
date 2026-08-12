package com.centerton.bodybuddy.global.response.code;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.centerton.bodybuddy.global.constant.StaticValue.*;

@Getter
@AllArgsConstructor
public enum ErrorResponseCode implements BaseResponseCode {
    MEAL_ITEMS_INVALID("MEAL_422_ITEMS", VALIDATION_ERROR, "음식 또는 섭취량을 확인해 주세요."),
    ONBOARDING_NOT_COMPLETED("USER_409_ONBOARDING", STATE_CONFLICT, "온보딩을 먼저 완료해 주세요."),
    MEAL_NOT_FOUND("MEAL_404", RESOURCE_NOT_FOUND, "식사 기록을 찾을 수 없습니다."),
    INVALID_MEAL_STATUS("MEAL_409_STATUS", STATE_CONFLICT, "현재 식사 상태에서는 요청을 처리할 수 없습니다."),
    MEAL_VERSION_CONFLICT("MEAL_409_VERSION", STATE_CONFLICT, "식사 기록이 변경되었습니다. 최신 정보를 다시 조회해 주세요."),
    BAD_REQUEST_ERROR("GLOBAL_400_1", BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_HTTP_MESSAGE_BODY("GLOBAL_400_2", BAD_REQUEST, "HTTP 요청 바디의 형식이 잘못되었습니다."),
    INVALID_HTTP_MESSAGE_PARAMETER("GLOBAL_400_3", BAD_REQUEST, "HTTP 요청 파라미터 형식이 잘못되었습니다."),
    UNAUTHORIZED_REQUEST("GLOBAL_401", UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED_REQUEST("GLOBAL_403", FORBIDDEN, "해당 요청에 접근 권한이 없습니다."),
    NOT_FOUND_ENDPOINT("GLOBAL_404", RESOURCE_NOT_FOUND, "존재하지 않는 앤드포인트입니다. 요청 URL을 확인해주세요."),
    UNSUPPORTED_HTTP_METHOD("GLOBAL_405", METHOD_NOT_ALLOWED, "지원하지 않는 메소드입니다."),
    RESOURCE_CONFLICT("GLOBAL_409", STATE_CONFLICT, "이미 존재하거나 충돌하는 리소스입니다."),
    IDEMPOTENCY_KEY_REUSED("GLOBAL_409_IDEMPOTENCY", STATE_CONFLICT, "같은 Idempotency-Key가 이미 다른 사용자 생성에 사용되었습니다."),
    REQUEST_ENTITY_TOO_LARGE("GLOBAL_413", PAYLOAD_TOO_LARGE, "요청 크기가 허용된 범위를 초과했습니다."),
    INVALID_MEDIA_TYPE("GLOBAL_415", UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입입니다."),
    INVALID_INPUT_VALUE("GLOBAL_422", VALIDATION_ERROR, "요청 값이 유효하지 않습니다."),
    TOO_MANY_REQUESTS("GLOBAL_429", RATE_LIMITED, "요청 횟수 제한을 초과했습니다. 잠시 후 다시 시도해주세요."),
    SERVER_ERROR("GLOBAL_500", INTERNAL_SERVER_ERROR, "서버 내부에서 알 수 없는 에러가 발생했습니다."),
    AI_BAD_RESPONSE("GLOBAL_502", AI_RESPONSE_INVALID, "AI 응답 처리 중 오류가 발생했습니다."),
    AI_SERVICE_UNAVAILABLE("GLOBAL_503", AI_UNAVAILABLE, "AI 서비스를 일시적으로 사용할 수 없습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
