package com.centerton.bodybuddy.domain.room.entity;

import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "meal_reactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_meal_reactions_room_meal_user_emoji",
                        columnNames = {
                                "room_id",
                                "meal_id",
                                "user_id",
                                "emoji_type"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_meal_reactions_room_meal",
                        columnList = "room_id, meal_id"
                ),
                @Index(
                        name = "idx_meal_reactions_user",
                        columnList = "user_id"
                )
        }
)
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MealReaction extends BaseEntity {

    @Id
    @Column(name = "reaction_id", length = 36)
    private String reactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "emoji_type", nullable = false, length = 30)
    private ReactionEmoji emojiType;

    public static MealReaction create(
            Room room,
            Meal meal,
            User user,
            ReactionEmoji emojiType
    ) {
        return MealReaction.builder()
                .reactionId(UUID.randomUUID().toString())
                .room(room)
                .meal(meal)
                .user(user)
                .emojiType(emojiType)
                .build();
    }
}