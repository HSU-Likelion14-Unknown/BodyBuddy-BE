package com.centerton.bodybuddy.domain.room.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
import com.centerton.bodybuddy.domain.room.dto.MealReactionsRes;
import com.centerton.bodybuddy.domain.room.dto.MealReactionsUpdateReq;
import com.centerton.bodybuddy.domain.room.dto.MealReactionsUpdateRes;
import com.centerton.bodybuddy.domain.room.entity.MealReaction;
import com.centerton.bodybuddy.domain.room.entity.ReactionEmoji;
import com.centerton.bodybuddy.domain.room.entity.Room;
import com.centerton.bodybuddy.domain.room.exception.RoomErrorCode;
import com.centerton.bodybuddy.domain.room.repository.MealReactionRepository;
import com.centerton.bodybuddy.domain.room.repository.RoomMemberRepository;
import com.centerton.bodybuddy.domain.room.repository.RoomRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MealReactionService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MealRepository mealRepository;
    private final MealReactionRepository mealReactionRepository;

    @Transactional
    public MealReactionsUpdateRes updateMyReactions(
            String authorization,
            String roomId,
            String mealId,
            MealReactionsUpdateReq request
    ) {
        User requester = AuthValidator.validateAndGetUser(
                authorization,
                userRepository
        );

        Room room = validateRoomMember(roomId, requester.getUserId());

        List<ReactionEmoji> requestedEmojis =
                validateAndNormalize(request.emojiTypes());

        Meal meal = mealRepository.findByIdForReactionUpdate(mealId)
                .orElseThrow(() ->
                        new BaseException(
                                RoomErrorCode.ROOM_MEAL_NOT_FOUND
                        )
                );

        validateSharedRoomMeal(roomId, meal);

        replaceMyReactions(
                room,
                meal,
                requester,
                requestedEmojis
        );

        List<MealReactionsUpdateRes.ReactionCount> counts =
                getUpdateReactionCounts(roomId, mealId);

        return MealReactionsUpdateRes.builder()
                .roomId(roomId)
                .mealId(mealId)
                .myReactions(requestedEmojis)
                .reactions(counts)
                .build();
    }

    @Transactional(readOnly = true)
    public MealReactionsRes getReactions(
            String authorization,
            String roomId,
            String mealId
    ) {
        User requester = AuthValidator.validateAndGetUser(
                authorization,
                userRepository
        );

        validateRoomMember(roomId, requester.getUserId());

        Meal meal = mealRepository.findByIdWithUser(mealId)
                .orElseThrow(() ->
                        new BaseException(
                                RoomErrorCode.ROOM_MEAL_NOT_FOUND
                        )
                );

        validateSharedRoomMeal(roomId, meal);

        List<ReactionEmoji> myReactions =
                getMyReactions(
                        roomId,
                        mealId,
                        requester.getUserId()
                );

        List<MealReactionsRes.ReactionCount> counts =
                getReactionCounts(roomId, mealId);

        return MealReactionsRes.builder()
                .roomId(roomId)
                .mealId(mealId)
                .myReactions(myReactions)
                .reactions(counts)
                .build();
    }

    private Room validateRoomMember(
            String roomId,
            String requesterId
    ) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() ->
                        new BaseException(
                                RoomErrorCode.ROOM_NOT_FOUND
                        )
                );

        if (!roomMemberRepository.existsByRoomIdAndUserId(
                roomId,
                requesterId
        )) {
            throw new BaseException(
                    RoomErrorCode.NOT_ROOM_MEMBER
            );
        }

        return room;
    }

    private List<ReactionEmoji> validateAndNormalize(
            List<ReactionEmoji> emojiTypes
    ) {
        Set<ReactionEmoji> uniqueEmojis =
                new LinkedHashSet<>(emojiTypes);

        if (uniqueEmojis.size() != emojiTypes.size()) {
            throw new BaseException(
                    RoomErrorCode.DUPLICATE_REACTION_EMOJI
            );
        }

        return List.copyOf(uniqueEmojis);
    }

    private void validateSharedRoomMeal(
            String roomId,
            Meal meal
    ) {
        User mealOwner = meal.getUser();

        boolean ownerIsRoomMember =
                roomMemberRepository.existsByRoomIdAndUserId(
                        roomId,
                        mealOwner.getUserId()
                );

        boolean sharingEnabled =
                Boolean.TRUE.equals(mealOwner.getShareToRoom());

        if (!ownerIsRoomMember || !sharingEnabled) {
            throw new BaseException(
                    RoomErrorCode.ROOM_MEAL_NOT_FOUND
            );
        }
    }

    private void replaceMyReactions(
            Room room,
            Meal meal,
            User requester,
            List<ReactionEmoji> emojiTypes
    ) {
        mealReactionRepository.deleteAllByRoomIdAndMealIdAndUserId(
                room.getRoomId(),
                meal.getMealId(),
                requester.getUserId()
        );

        if (emojiTypes.isEmpty()) {
            return;
        }

        List<MealReaction> reactions = emojiTypes.stream()
                .map(emojiType -> MealReaction.create(
                        room,
                        meal,
                        requester,
                        emojiType
                ))
                .toList();

        mealReactionRepository.saveAllAndFlush(reactions);
    }

    private List<ReactionEmoji> getMyReactions(
            String roomId,
            String mealId,
            String userId
    ) {
        return mealReactionRepository
                .findAllByRoomRoomIdAndMealMealIdAndUserUserIdOrderByEmojiTypeAsc(
                        roomId,
                        mealId,
                        userId
                )
                .stream()
                .map(MealReaction::getEmojiType)
                .toList();
    }

    private List<MealReactionsUpdateRes.ReactionCount>
    getUpdateReactionCounts(
            String roomId,
            String mealId
    ) {
        return mealReactionRepository
                .countByRoomIdAndMealIdGroupByEmojiType(
                        roomId,
                        mealId
                )
                .stream()
                .map(row ->
                        MealReactionsUpdateRes.ReactionCount.builder()
                                .emojiType((ReactionEmoji) row[0])
                                .count((Long) row[1])
                                .build()
                )
                .toList();
    }

    private List<MealReactionsRes.ReactionCount>
    getReactionCounts(
            String roomId,
            String mealId
    ) {
        return mealReactionRepository
                .countByRoomIdAndMealIdGroupByEmojiType(
                        roomId,
                        mealId
                )
                .stream()
                .map(row ->
                        MealReactionsRes.ReactionCount.builder()
                                .emojiType((ReactionEmoji) row[0])
                                .count((Long) row[1])
                                .build()
                )
                .toList();
    }
}