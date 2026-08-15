package com.centerton.bodybuddy.domain.food.entity;

import com.centerton.bodybuddy.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "foods")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Food extends BaseEntity {

    @Id
    @Column(name = "food_id", length = 36)
    private String foodId;

    @Column(name = "canonical_name", nullable = false, length = 200)
    private String canonicalName;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "data_source", nullable = false, length = 30)
    private String dataSource;

    @Column(name = "source_food_code", length = 50)
    private String sourceFoodCode;

    @Column(name = "food_group", length = 100)
    private String foodGroup;

    @Column(name = "food_type", nullable = false, length = 20)
    private String foodType;

    @Column(name = "ingredient_name", length = 100)
    private String ingredientName;

    @Column(name = "processing_state", length = 50)
    private String processingState;

    @Column(name = "source_version", length = 20)
    private String sourceVersion;

    @Column(name = "source_name", length = 100)
    private String sourceName;

    @Column(name = "is_recommendation_candidate", nullable = false)
    private boolean recommendationCandidate;

    @Column(name = "representative_method", length = 40)
    private String representativeMethod;

    @Column(name = "variant_count", nullable = false)
    private int variantCount;

    @Column(name = "nutrition_data_quality", nullable = false, length = 20)
    private String nutritionDataQuality;

    @Column(name = "trace_nutrients", columnDefinition = "json")
    private String traceNutrients;

    @Column(name = "qualified_nutrients", columnDefinition = "json")
    private String qualifiedNutrients;
}
