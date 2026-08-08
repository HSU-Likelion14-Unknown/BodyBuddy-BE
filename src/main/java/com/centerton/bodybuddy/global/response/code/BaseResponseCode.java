package com.centerton.bodybuddy.global.response.code;

public interface BaseResponseCode {
    String getCode();
    int getHttpStatus();
    String getMessage();
}
