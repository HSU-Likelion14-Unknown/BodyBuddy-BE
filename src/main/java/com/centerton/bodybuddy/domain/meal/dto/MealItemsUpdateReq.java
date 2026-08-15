package com.centerton.bodybuddy.domain.meal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MealItemsUpdateReq {

    @Valid
    @NotEmpty(message = "수정할 음식을 한 개 이상 입력해 주세요.")
    @Size(max = 20, message = "음식은 최대 20개까지 입력할 수 있습니다.")
    private List<MealItemInputReq> items;
}
