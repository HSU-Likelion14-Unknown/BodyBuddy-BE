package com.centerton.bodybuddy.domain.recommendation.entity;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.global.entity.BaseEntity;
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
@Table(name = "ingredient_dish_mappings")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IngredientDishMapping extends BaseEntity {

    @Id
    @Column(name = "mapping_id", length = 36)
    private String mappingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_food_id", nullable = false)
    private Food ingredientFood;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dish_id", nullable = false)
    private DishTemplate dish;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "active", nullable = false)
    private boolean active;
}
