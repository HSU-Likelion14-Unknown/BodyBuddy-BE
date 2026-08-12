package com.centerton.bodybuddy.domain.meal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class TextMealCreateReq {

    @NotBlank(message = "식사 내용을 입력해 주세요.")
    @Size(max = 500, message = "식사 내용은 500자 이하여야 합니다.")
    private String text;

    @NotNull(message = "식사 시각을 입력해 주세요.")
    private OffsetDateTime eatenAt;
}
