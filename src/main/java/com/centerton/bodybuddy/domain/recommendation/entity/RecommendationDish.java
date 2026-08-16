package com.centerton.bodybuddy.domain.recommendation.entity;

import com.centerton.bodybuddy.domain.food.entity.Food;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recommendation_dishes")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RecommendationDish {

    @Id
    @Column(name = "recommendation_dish_id", length = 36)
    private String recommendationDishId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private RecommendationIngredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id")
    private Food food;

    @Column(name = "dish_name", nullable = false, length = 200)
    private String dishName;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;
}
