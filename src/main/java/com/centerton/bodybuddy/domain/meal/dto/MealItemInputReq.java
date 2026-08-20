package com.centerton.bodybuddy.domain.meal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MealItemInputReq {

    @Size(max = 36, message = "음식 ID는 36자 이하여야 합니다.")
    private String foodId;

    @NotBlank(message = "음식명을 입력해 주세요.")
    @Size(max = 100, message = "음식명은 100자 이하여야 합니다.")
    private String foodName;

    @NotNull(message = "섭취량을 입력해 주세요.")
    @DecimalMin(value = "0.01", message = "섭취량은 0보다 커야 합니다.")
    private BigDecimal amount;

    @NotBlank(message = "섭취 단위를 입력해 주세요.")
    @Pattern(regexp = "인분", message = "섭취 단위는 인분만 사용할 수 있습니다.")
    @Size(max = 30, message = "섭취 단위는 30자 이하여야 합니다.")
    private String unit;
}
