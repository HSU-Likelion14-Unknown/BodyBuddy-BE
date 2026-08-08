package com.centerton.bodybuddy.global.response;

import com.centerton.bodybuddy.global.response.code.BaseResponseCode;
import com.centerton.bodybuddy.global.response.code.SuccessResponseCode;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@JsonPropertyOrder({"isSuccess","timestamp", "code", "httpStatus", "message", "data"})
public class SuccessResponse<T> extends BaseResponse {
    private final int httpStatus;
    private final T data;

    @Builder
    public SuccessResponse(T data, BaseResponseCode baseResponseCode){
        super(true, baseResponseCode.getCode(), baseResponseCode.getMessage());
        this.httpStatus = baseResponseCode.getHttpStatus();
        this.data = data;
    }

    public static <T> SuccessResponse<T> created(T data){
        return new SuccessResponse<>(data, SuccessResponseCode.SUCCESS_CREATED);
    }

    public static <T> SuccessResponse<T> from(T data){
        return new SuccessResponse<>(data, SuccessResponseCode.SUCCESS_OK);
    }

    public static <T> SuccessResponse<T> accepted(T data){
        return new SuccessResponse<>(data, SuccessResponseCode.SUCCESS_ACCEPTED);
    }

    public static SuccessResponse<?> empty(){
        return new SuccessResponse<>(null, SuccessResponseCode.SUCCESS_OK);
    }

    public static SuccessResponse<?> noContent(){
        return new SuccessResponse<>(null, SuccessResponseCode.SUCCESS_NO_CONTENT);
    }

    public static SuccessResponse<Void> empty(BaseResponseCode baseResponseCode){
        return new SuccessResponse<>(null, baseResponseCode);
    }

    public static <T> SuccessResponse<T> of(T data, BaseResponseCode baseResponseCode){
        return new SuccessResponse<>(data, baseResponseCode);
    }
}

