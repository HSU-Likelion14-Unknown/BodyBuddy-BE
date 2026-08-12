package com.centerton.bodybuddy.domain.meal.entity;

import com.centerton.bodybuddy.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "meal_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_meal_items_order", columnNames = {"meal_id", "sort_order"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MealItem extends BaseEntity {

    @Id
    @Column(name = "meal_item_id", length = 36)
    private String mealItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @Column(name = "food_id", length = 36)
    private String foodId;

    @Column(name = "food_name", nullable = false, length = 100)
    private String foodName;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_unit", length = 30)
    private String amountUnit;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private MealItemSource source;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Embedded
    private NutritionValues nutrition;
}
