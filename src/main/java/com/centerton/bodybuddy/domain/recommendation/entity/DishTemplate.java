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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "dish_templates")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DishTemplate extends BaseEntity {

    @Id
    @Column(name = "dish_id", length = 36)
    private String dishId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id")
    private Food food;

    @Column(name = "dish_name", nullable = false, length = 200)
    private String dishName;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ingredient_names", nullable = false, columnDefinition = "json")
    private List<String> ingredientNames;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allergen_codes", nullable = false, columnDefinition = "json")
    private List<String> allergenCodes;

    @Column(name = "active", nullable = false)
    private boolean active;
}
