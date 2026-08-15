package com.centerton.bodybuddy.domain.meal.entity;

import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meals", indexes = {
        @Index(name = "idx_meals_user_eaten", columnList = "user_id,eaten_at"),
        @Index(name = "idx_meals_user_status", columnList = "user_id,status")
})
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Meal extends BaseEntity {

    @Id
    @Column(name = "meal_id", length = 36)
    private String mealId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 16)
    private MealInputType inputType;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_source", length = 16)
    private ImageSource imageSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private MealStatus status;

    @Column(name = "photo_object_key", length = 500)
    private String photoObjectKey;

    @Column(name = "direct_input_text", length = 500)
    private String directInputText;

    @Column(name = "eaten_at", nullable = false)
    private LocalDateTime eatenAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Meal createText(User user, String text, LocalDateTime eatenAt) {
        return Meal.builder()
                .mealId(UUID.randomUUID().toString())
                .user(user)
                .inputType(MealInputType.TEXT)
                .status(MealStatus.ANALYZING)
                .directInputText(text)
                .eatenAt(eatenAt)
                .build();
    }

    public static Meal createImage(User user, ImageSource imageSource, String photoObjectKey,
                                   LocalDateTime eatenAt) {
        return Meal.builder()
                .mealId(UUID.randomUUID().toString())
                .user(user)
                .inputType(MealInputType.IMAGE)
                .imageSource(imageSource)
                .status(MealStatus.ANALYZING)
                .photoObjectKey(photoObjectKey)
                .eatenAt(eatenAt)
                .build();
    }

    public void markReviewRequired() {
        this.status = MealStatus.REVIEW_REQUIRED;
    }

    public void markFailed() {
        this.status = MealStatus.FAILED;
    }

    public void confirm(LocalDateTime confirmedAt) {
        this.status = MealStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    public void complete(LocalDateTime completedAt) {
        this.status = MealStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void updateEatenAt(LocalDateTime eatenAt) {
        this.eatenAt = eatenAt;
    }

}
