package com.centerton.bodybuddy.domain.recommendation.entity;

import com.centerton.bodybuddy.domain.food.entity.Food;
import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "recommendation_ingredients")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RecommendationIngredient extends BaseEntity {

    @Id
    @Column(name = "ingredient_id", length = 36)
    private String ingredientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private Recommendation recommendation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    @Column(name = "ingredient_name", nullable = false, length = 200)
    private String ingredientName;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nutrition_snapshot", columnDefinition = "json")
    private NutritionValues nutritionSnapshot;
}
