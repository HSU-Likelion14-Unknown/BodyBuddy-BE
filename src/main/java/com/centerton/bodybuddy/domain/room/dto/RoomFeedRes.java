package com.centerton.bodybuddy.domain.room.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record RoomFeedRes(
        String roomId,
        List<FeedItem> feeds
) {
    @Builder
    public record FeedItem(
            String userId,
            String nickname,
            String mealId,
            String photoUrl,
            List<String> foodNames,
            LocalDateTime eatenAt
    ) {
    }
}