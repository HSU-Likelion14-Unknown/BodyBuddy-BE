-- Expand the existing BodyBuddy food catalog for KFCT 10.4 ingredient data.
-- Target: MySQL 8.0+, existing bodybuddy_db created from db/schema.sql.
-- Apply once before db/data/food_catalog_10_4_seed.sql.

USE bodybuddy_db;

ALTER TABLE foods
    MODIFY COLUMN canonical_name VARCHAR(200) NOT NULL,
    MODIFY COLUMN normalized_name VARCHAR(200) NOT NULL,
    DROP INDEX uk_foods_normalized_name,
    ADD COLUMN data_source VARCHAR(30) NOT NULL DEFAULT 'LEGACY' AFTER active,
    ADD COLUMN source_food_code VARCHAR(50) NULL AFTER data_source,
    ADD COLUMN food_group VARCHAR(100) NULL AFTER source_food_code,
    ADD COLUMN food_type VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' AFTER food_group,
    ADD COLUMN ingredient_name VARCHAR(100) NULL AFTER food_type,
    ADD COLUMN processing_state VARCHAR(50) NULL AFTER ingredient_name,
    ADD COLUMN source_version VARCHAR(20) NULL AFTER processing_state,
    ADD COLUMN source_name VARCHAR(100) NULL AFTER source_version,
    ADD COLUMN is_recommendation_candidate BOOLEAN NOT NULL DEFAULT FALSE AFTER source_name,
    ADD COLUMN representative_method VARCHAR(40) NULL AFTER is_recommendation_candidate,
    ADD COLUMN variant_count INT UNSIGNED NOT NULL DEFAULT 1 AFTER representative_method,
    ADD COLUMN nutrition_data_quality VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' AFTER variant_count,
    ADD COLUMN trace_nutrients JSON NULL AFTER nutrition_data_quality,
    ADD COLUMN qualified_nutrients JSON NULL AFTER trace_nutrients,
    ADD UNIQUE KEY uk_foods_source_code (data_source, source_food_code),
    ADD INDEX idx_foods_normalized_name (normalized_name),
    ADD INDEX idx_foods_ingredient_name (ingredient_name),
    ADD INDEX idx_foods_recommendation_candidate (food_type, is_recommendation_candidate, active),
    ADD CONSTRAINT chk_foods_type CHECK (food_type IN ('INGREDIENT', 'DISH', 'UNKNOWN')),
    ADD CONSTRAINT chk_foods_nutrition_quality CHECK (
        nutrition_data_quality IN ('MEASURED', 'QUALIFIED', 'PARTIAL', 'UNKNOWN')
    );

ALTER TABLE food_nutritions
    MODIFY COLUMN calories_kcal DECIMAL(12,2) NULL,
    MODIFY COLUMN carbohydrate_g DECIMAL(12,2) NULL,
    MODIFY COLUMN protein_g DECIMAL(12,2) NULL,
    MODIFY COLUMN fat_g DECIMAL(12,2) NULL,
    MODIFY COLUMN fiber_g DECIMAL(12,2) NULL,
    MODIFY COLUMN sodium_mg DECIMAL(12,2) NULL,
    ADD COLUMN calcium_mg DECIMAL(12,2) NULL AFTER sodium_mg,
    ADD COLUMN iron_mg DECIMAL(12,2) NULL AFTER calcium_mg,
    ADD COLUMN potassium_mg DECIMAL(12,2) NULL AFTER iron_mg,
    ADD COLUMN vitamin_a_mcg_rae DECIMAL(12,2) NULL AFTER potassium_mg,
    ADD COLUMN vitamin_c_mg DECIMAL(12,2) NULL AFTER vitamin_a_mcg_rae,
    ADD CONSTRAINT chk_food_nutritions_micronutrients_nonnegative CHECK (
        (calcium_mg IS NULL OR calcium_mg >= 0)
        AND (iron_mg IS NULL OR iron_mg >= 0)
        AND (potassium_mg IS NULL OR potassium_mg >= 0)
        AND (vitamin_a_mcg_rae IS NULL OR vitamin_a_mcg_rae >= 0)
        AND (vitamin_c_mg IS NULL OR vitamin_c_mg >= 0)
    );

ALTER TABLE meal_items
    MODIFY COLUMN calories_kcal DECIMAL(12,2) NULL,
    MODIFY COLUMN carbohydrate_g DECIMAL(12,2) NULL,
    MODIFY COLUMN protein_g DECIMAL(12,2) NULL,
    MODIFY COLUMN fat_g DECIMAL(12,2) NULL,
    MODIFY COLUMN fiber_g DECIMAL(12,2) NULL,
    MODIFY COLUMN sodium_mg DECIMAL(12,2) NULL,
    ADD COLUMN calcium_mg DECIMAL(12,2) NULL AFTER sodium_mg,
    ADD COLUMN iron_mg DECIMAL(12,2) NULL AFTER calcium_mg,
    ADD COLUMN potassium_mg DECIMAL(12,2) NULL AFTER iron_mg,
    ADD COLUMN vitamin_a_mcg_rae DECIMAL(12,2) NULL AFTER potassium_mg,
    ADD COLUMN vitamin_c_mg DECIMAL(12,2) NULL AFTER vitamin_a_mcg_rae,
    ADD CONSTRAINT chk_meal_items_micronutrients_nonnegative CHECK (
        (calcium_mg IS NULL OR calcium_mg >= 0)
        AND (iron_mg IS NULL OR iron_mg >= 0)
        AND (potassium_mg IS NULL OR potassium_mg >= 0)
        AND (vitamin_a_mcg_rae IS NULL OR vitamin_a_mcg_rae >= 0)
        AND (vitamin_c_mg IS NULL OR vitamin_c_mg >= 0)
    );

ALTER TABLE meal_nutrition_summaries
    ADD COLUMN calcium_mg DECIMAL(12,2) NULL AFTER sodium_mg,
    ADD COLUMN iron_mg DECIMAL(12,2) NULL AFTER calcium_mg,
    ADD COLUMN potassium_mg DECIMAL(12,2) NULL AFTER iron_mg,
    ADD COLUMN vitamin_a_mcg_rae DECIMAL(12,2) NULL AFTER potassium_mg,
    ADD COLUMN vitamin_c_mg DECIMAL(12,2) NULL AFTER vitamin_a_mcg_rae,
    ADD CONSTRAINT chk_meal_nutrition_micronutrients_nonnegative CHECK (
        (calcium_mg IS NULL OR calcium_mg >= 0)
        AND (iron_mg IS NULL OR iron_mg >= 0)
        AND (potassium_mg IS NULL OR potassium_mg >= 0)
        AND (vitamin_a_mcg_rae IS NULL OR vitamin_a_mcg_rae >= 0)
        AND (vitamin_c_mg IS NULL OR vitamin_c_mg >= 0)
    );

CREATE TABLE food_aliases (
    food_alias_id CHAR(36) NOT NULL,
    food_id CHAR(36) NOT NULL,
    alias_name VARCHAR(100) NOT NULL,
    normalized_alias VARCHAR(100) NOT NULL,
    alias_type VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (food_alias_id),
    UNIQUE KEY uk_food_aliases_normalized (normalized_alias),
    INDEX idx_food_aliases_food (food_id),
    CONSTRAINT chk_food_aliases_type CHECK (alias_type IN ('INGREDIENT_BASE', 'COMMON_NAME', 'AI_SYNONYM')),
    CONSTRAINT fk_food_aliases_food FOREIGN KEY (food_id) REFERENCES foods (food_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
