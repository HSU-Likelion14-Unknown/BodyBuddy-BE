package com.centerton.bodybuddy.domain.room.dto;

import com.centerton.bodybuddy.domain.room.entity.ReactionEmoji;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MealReactionsUpdateReq(

        @NotNull(message = "emojiTypes는 필수입니다.")
        @Size(
                max = 26,
                message = "선택할 수 있는 이모지 개수를 초과했습니다."
        )
        List<@NotNull(message = "이모지는 null일 수 없습니다.")
                ReactionEmoji> emojiTypes
) {
}