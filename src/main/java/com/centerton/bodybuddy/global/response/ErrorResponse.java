package com.centerton.bodybuddy.global.response;

import com.centerton.bodybuddy.global.response.code.BaseResponseCode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@JsonPropertyOrder({"timestamp", "status", "code", "message", "fieldErrors", "traceId"})
public class ErrorResponse<T> {

    private final Instant timestamp;
    private final int status;
    private final String code;
    private final String message;
    private final List<FieldErrorDetail> fieldErrors;
    private final String traceId;

    private ErrorResponse(BaseResponseCode responseCode, String message,
                          List<FieldErrorDetail> fieldErrors) {
        this.timestamp = Instant.now();
        this.status = responseCode.getHttpStatus();
        this.code = responseCode.getCode();
        this.message = message;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        String currentTraceId = MDC.get("traceId");
        this.traceId = currentTraceId == null ? UUID.randomUUID().toString() : currentTraceId;
    }

    public static ErrorResponse<?> from(BaseResponseCode responseCode) {
        return new ErrorResponse<>(responseCode, responseCode.getMessage(), List.of());
    }

    public static ErrorResponse<?> of(BaseResponseCode responseCode, String message) {
        return new ErrorResponse<>(responseCode, message, List.of());
    }

    public static ErrorResponse<?> validation(BaseResponseCode responseCode,
                                              List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse<>(responseCode, responseCode.getMessage(), fieldErrors);
    }

    @JsonIgnore
    public int getHttpStatus() {
        return status;
    }
}
