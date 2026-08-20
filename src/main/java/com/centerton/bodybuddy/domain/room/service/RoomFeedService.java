package com.centerton.bodybuddy.domain.room.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.MealStatus;
import com.centerton.bodybuddy.domain.meal.repository.MealItemRepository;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
import com.centerton.bodybuddy.domain.room.dto.RoomFeedRes;
import com.centerton.bodybuddy.domain.room.entity.RoomMember;
import com.centerton.bodybuddy.domain.room.exception.RoomErrorCode;
import com.centerton.bodybuddy.domain.room.repository.RoomMemberRepository;
import com.centerton.bodybuddy.domain.room.repository.RoomRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomFeedService {

    private static final ZoneId SERVICE_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private static final List<MealStatus> FEED_STATUSES =
            List.of(
                    MealStatus.CONFIRMED,
                    MealStatus.COMPLETED
            );

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;

    @Transactional(readOnly = true)
    public RoomFeedRes getFeed(
            String authorization,
            String roomId,
            LocalDate date
    ) {
        User requester = AuthValidator.validateAndGetUser(
                authorization,
                userRepository
        );

        validateRoomMember(roomId, requester.getUserId());

        LocalDate targetDate =
                date == null
                        ? LocalDate.now(SERVICE_ZONE_ID)
                        : date;

        List<String> memberUserIds =
                getRoomMemberUserIds(roomId);

        LocalDateTime startAt =
                targetDate.atStartOfDay();

        LocalDateTime endAt =
                targetDate.plusDays(1).atStartOfDay();

        List<Meal> meals =
                mealRepository.findSharedRoomFeed(
                        memberUserIds,
                        startAt,
                        endAt,
                        FEED_STATUSES
                );

        Map<String, List<String>> foodNamesByMealId =
                getFoodNamesByMealId(meals);

        List<RoomFeedRes.FeedItem> feeds =
                meals.stream()
                        .map(meal ->
                                toFeedItem(
                                        meal,
                                        foodNamesByMealId.getOrDefault(
                                                meal.getMealId(),
                                                List.of()
                                        )
                                )
                        )
                        .toList();

        return RoomFeedRes.builder()
                .roomId(roomId)
                .feeds(feeds)
                .build();
    }

    private void validateRoomMember(
            String roomId,
            String requesterId
    ) {
        if (!roomRepository.existsById(roomId)) {
            throw new BaseException(
                    RoomErrorCode.ROOM_NOT_FOUND
            );
        }

        if (!roomMemberRepository.existsByRoomIdAndUserId(
                roomId,
                requesterId
        )) {
            throw new BaseException(
                    RoomErrorCode.NOT_ROOM_MEMBER
            );
        }
    }

    private List<String> getRoomMemberUserIds(
            String roomId
    ) {
        return roomMemberRepository.findByRoomId(roomId)
                .stream()
                .map(RoomMember::getUserId)
                .toList();
    }

    private Map<String, List<String>> getFoodNamesByMealId(
            List<Meal> meals
    ) {
        if (meals.isEmpty()) {
            return Map.of();
        }

        List<String> mealIds = meals.stream()
                .map(Meal::getMealId)
                .toList();

        return mealItemRepository.findFoodNamesByMealIds(mealIds)
                .stream()
                .collect(
                        Collectors.groupingBy(
                                row -> (String) row[0],
                                Collectors.mapping(
                                        row -> (String) row[1],
                                        Collectors.toList()
                                )
                        )
                );
    }

    private RoomFeedRes.FeedItem toFeedItem(
            Meal meal,
            List<String> foodNames
    ) {
        return RoomFeedRes.FeedItem.builder()
                .userId(meal.getUser().getUserId())
                .nickname(meal.getUser().getNickname())
                .profileImageUrl(
                        meal.getUser().getProfileImageUrl()
                )
                .mealId(meal.getMealId())
                .photoUrl(
                        createPhotoUrl(
                                meal.getPhotoObjectKey()
                        )
                )
                .foodNames(foodNames)
                .eatenAt(meal.getEatenAt())
                .build();
    }

    private String createPhotoUrl(
            String photoObjectKey
    ) {
        if (photoObjectKey == null
                || photoObjectKey.isBlank()) {
            return null;
        }

        return "/api/v1/meals/images/"
                + photoObjectKey;
    }
}