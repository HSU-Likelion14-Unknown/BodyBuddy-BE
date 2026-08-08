package com.centerton.bodybuddy.global.response.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import static com.centerton.bodybuddy.global.constant.StaticValue.*;

@Getter
@AllArgsConstructor
public enum SuccessResponseCode implements BaseResponseCode{
    SUCCESS_OK("GLOBAL_200", OK, "호출에 성공하였습니다."),
    SUCCESS_CREATED("GLOBAL_201", CREATED, "생성에 성공하였습니다."),
    SUCCESS_ACCEPTED("GLOBAL_202", ACCEPTED, "요청이 접수되었습니다. 처리가 진행 중입니다."),
    SUCCESS_NO_CONTENT("GLOBAL_204", NO_CONTENT, "요청이 성공적으로 처리되었습니다. 반환할 데이터가 없습니다.");

    private String code;
    private int httpStatus;
    private String message;
}
