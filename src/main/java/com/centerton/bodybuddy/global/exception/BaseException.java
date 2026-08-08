package com.centerton.bodybuddy.global.exception;

import com.centerton.bodybuddy.global.response.code.BaseResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BaseException extends RuntimeException{
    private BaseResponseCode baseResponseCode;
}
