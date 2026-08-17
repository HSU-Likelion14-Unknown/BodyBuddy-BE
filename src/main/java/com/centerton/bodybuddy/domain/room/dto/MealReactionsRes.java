package com.centerton.bodybuddy.domain.room.dto;

import com.centerton.bodybuddy.domain.room.entity.ReactionEmoji;
import lombok.Builder;

import java.util.List;

@Builder
public record MealReactionsRes(
        String roomId,
        String mealId,
        List<ReactionEmoji> myReactions,
        List<ReactionCount> reactions
) {
    @Builder
    public record ReactionCount(
            ReactionEmoji emojiType,
            long count
    ) {
    }
}