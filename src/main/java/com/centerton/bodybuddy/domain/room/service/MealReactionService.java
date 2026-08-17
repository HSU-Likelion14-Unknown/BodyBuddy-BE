package com.centerton.bodybuddy.domain.room.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.repository.MealRepository;
import com.centerton.bodybuddy.domain.room.dto.MealReactionsUpdateReq;
import com.centerton.bodybuddy.domain.room.dto.MealReactionsUpdateRes;
import com.centerton.bodybuddy.domain.room.entity.MealReaction;
import com.centerton.bodybuddy.domain.room.entity.ReactionEmoji;
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

        validateRoomMember(roomId, requester.getUserId());

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
                meal,
                requester,
                requestedEmojis
        );

        List<MealReactionsUpdateRes.ReactionCount> counts =
                getReactionCounts(mealId);

        return MealReactionsUpdateRes.builder()
                .roomId(roomId)
                .mealId(mealId)
                .myReactions(requestedEmojis)
                .reactions(counts)
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
            Meal meal,
            User requester,
            List<ReactionEmoji> emojiTypes
    ) {
        mealReactionRepository.deleteAllByMealIdAndUserId(
                meal.getMealId(),
                requester.getUserId()
        );

        if (emojiTypes.isEmpty()) {
            return;
        }

        List<MealReaction> reactions = emojiTypes.stream()
                .map(emojiType -> MealReaction.create(
                        meal,
                        requester,
                        emojiType
                ))
                .toList();

        mealReactionRepository.saveAllAndFlush(reactions);
    }

    private List<MealReactionsUpdateRes.ReactionCount>
    getReactionCounts(String mealId) {
        return mealReactionRepository
                .countByMealIdGroupByEmojiType(mealId)
                .stream()
                .map(row ->
                        MealReactionsUpdateRes.ReactionCount.builder()
                                .emojiType((ReactionEmoji) row[0])
                                .count((Long) row[1])
                                .build()
                )
                .toList();
    }
}