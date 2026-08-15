-- BodyBuddy MVP schema for MySQL 8.0+
-- Store DATETIME values in UTC. Interpret daily boundaries in Asia/Seoul in the application layer.
-- WARNING: Running this file deletes the existing bodybuddy_db and all of its data.

DROP DATABASE IF EXISTS bodybuddy_db;
CREATE DATABASE bodybuddy_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
USE bodybuddy_db;

CREATE TABLE users (
    user_id CHAR(36) NOT NULL,
    access_key_hash CHAR(64) NOT NULL COMMENT 'SHA-256 of the opaque access key',
    nickname VARCHAR(50) NULL,
    birth_year SMALLINT UNSIGNED NULL,
    gender VARCHAR(24) NULL,
    allergies JSON NULL COMMENT 'Array of standardized allergy codes, e.g. ["EGG","MILK"]',
    disliked_foods JSON NULL COMMENT 'Array of normalized food names, e.g. ["고수","가지"]',
    onboarding_completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_access_key_hash (access_key_hash),
    CONSTRAINT chk_users_birth_year CHECK (birth_year IS NULL OR birth_year BETWEEN 1900 AND 2100),
    CONSTRAINT chk_users_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'PREFER_NOT_TO_SAY')),
    CONSTRAINT chk_users_allergies_json CHECK (allergies IS NULL OR JSON_TYPE(allergies) = 'ARRAY'),
    CONSTRAINT chk_users_disliked_foods_json CHECK (disliked_foods IS NULL OR JSON_TYPE(disliked_foods) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(64) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (idempotency_key),
    INDEX idx_idempotency_keys_user (user_id),
    CONSTRAINT fk_idempotency_keys_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE foods (
    food_id CHAR(36) NOT NULL,
    canonical_name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    data_source VARCHAR(30) NOT NULL DEFAULT 'LEGACY',
    source_food_code VARCHAR(50) NULL,
    food_group VARCHAR(100) NULL,
    food_type VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ingredient_name VARCHAR(100) NULL,
    processing_state VARCHAR(50) NULL,
    source_version VARCHAR(20) NULL,
    source_name VARCHAR(100) NULL,
    is_recommendation_candidate BOOLEAN NOT NULL DEFAULT FALSE,
    representative_method VARCHAR(40) NULL,
    variant_count INT UNSIGNED NOT NULL DEFAULT 1,
    nutrition_data_quality VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    trace_nutrients JSON NULL,
    qualified_nutrients JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (food_id),
    UNIQUE KEY uk_foods_source_code (data_source, source_food_code),
    INDEX idx_foods_normalized_name (normalized_name),
    INDEX idx_foods_ingredient_name (ingredient_name),
    INDEX idx_foods_recommendation_candidate (food_type, is_recommendation_candidate, active),
    CONSTRAINT chk_foods_type CHECK (food_type IN ('INGREDIENT', 'DISH', 'UNKNOWN')),
    CONSTRAINT chk_foods_nutrition_quality CHECK (
        nutrition_data_quality IN ('MEASURED', 'QUALIFIED', 'PARTIAL', 'UNKNOWN')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Nutrition values supplied by the food catalog. This is separate from
-- meal_nutrition_summaries, which stores the calculated result for one meal.
CREATE TABLE food_nutritions (
    food_id CHAR(36) NOT NULL,
    reference_amount DECIMAL(10,2) NOT NULL,
    reference_unit VARCHAR(20) NOT NULL,
    calories_kcal DECIMAL(10,2) NULL,
    carbohydrate_g DECIMAL(10,2) NULL,
    protein_g DECIMAL(10,2) NULL,
    fat_g DECIMAL(10,2) NULL,
    fiber_g DECIMAL(10,2) NULL,
    sodium_mg DECIMAL(10,2) NULL,
    calcium_mg DECIMAL(10,2) NULL,
    iron_mg DECIMAL(10,2) NULL,
    potassium_mg DECIMAL(10,2) NULL,
    vitamin_a_mcg_rae DECIMAL(10,2) NULL,
    vitamin_c_mg DECIMAL(10,2) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (food_id),
    CONSTRAINT chk_food_nutritions_reference CHECK (reference_amount > 0),
    CONSTRAINT chk_food_nutritions_nonnegative CHECK (
        (calories_kcal IS NULL OR calories_kcal >= 0)
        AND (carbohydrate_g IS NULL OR carbohydrate_g >= 0)
        AND (protein_g IS NULL OR protein_g >= 0)
        AND (fat_g IS NULL OR fat_g >= 0)
        AND (fiber_g IS NULL OR fiber_g >= 0)
        AND (sodium_mg IS NULL OR sodium_mg >= 0)
        AND (calcium_mg IS NULL OR calcium_mg >= 0)
        AND (iron_mg IS NULL OR iron_mg >= 0)
        AND (potassium_mg IS NULL OR potassium_mg >= 0)
        AND (vitamin_a_mcg_rae IS NULL OR vitamin_a_mcg_rae >= 0)
        AND (vitamin_c_mg IS NULL OR vitamin_c_mg >= 0)
    ),
    CONSTRAINT fk_food_nutritions_food FOREIGN KEY (food_id) REFERENCES foods (food_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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

CREATE TABLE meals (
    meal_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    input_type VARCHAR(16) NOT NULL,
    image_source VARCHAR(16) NULL,
    status VARCHAR(24) NOT NULL,
    photo_object_key VARCHAR(500) NULL,
    direct_input_text VARCHAR(500) NULL,
    eaten_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (meal_id),
    UNIQUE KEY uk_meals_id_user (meal_id, user_id),
    CONSTRAINT chk_meals_input_type CHECK (input_type IN ('IMAGE', 'TEXT')),
    CONSTRAINT chk_meals_image_source CHECK (
        (input_type = 'IMAGE' AND image_source IS NOT NULL AND image_source IN ('CAMERA', 'GALLERY') AND photo_object_key IS NOT NULL)
        OR
        (input_type = 'TEXT' AND image_source IS NULL AND photo_object_key IS NULL AND direct_input_text IS NOT NULL)
    ),
    CONSTRAINT chk_meals_status CHECK (status IN ('ANALYZING', 'REVIEW_REQUIRED', 'REANALYZING', 'CONFIRMED', 'COMPLETED', 'FAILED')),
    INDEX idx_meals_user_eaten_deleted (user_id, eaten_at, deleted_at),
    INDEX idx_meals_user_status (user_id, status),
    CONSTRAINT fk_meals_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE meal_items (
    meal_item_id CHAR(36) NOT NULL,
    meal_id CHAR(36) NOT NULL,
    food_id CHAR(36) NULL,
    food_name VARCHAR(100) NOT NULL,
    amount DECIMAL(10,2) NULL,
    amount_unit VARCHAR(30) NULL,
    confidence DECIMAL(5,4) NULL,
    source VARCHAR(20) NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    calories_kcal DECIMAL(10,2) NULL,
    carbohydrate_g DECIMAL(10,2) NULL,
    protein_g DECIMAL(10,2) NULL,
    fat_g DECIMAL(10,2) NULL,
    fiber_g DECIMAL(10,2) NULL,
    sodium_mg DECIMAL(10,2) NULL,
    calcium_mg DECIMAL(10,2) NULL,
    iron_mg DECIMAL(10,2) NULL,
    potassium_mg DECIMAL(10,2) NULL,
    vitamin_a_mcg_rae DECIMAL(10,2) NULL,
    vitamin_c_mg DECIMAL(10,2) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (meal_item_id),
    UNIQUE KEY uk_meal_items_order (meal_id, sort_order),
    INDEX idx_meal_items_food (food_id),
    CONSTRAINT chk_meal_items_amount CHECK (
        (amount IS NULL AND amount_unit IS NULL)
        OR
        (amount > 0 AND amount_unit IS NOT NULL)
    ),
    CONSTRAINT chk_meal_items_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_meal_items_nutrition_nonnegative CHECK (
        (calories_kcal IS NULL OR calories_kcal >= 0)
        AND (carbohydrate_g IS NULL OR carbohydrate_g >= 0)
        AND (protein_g IS NULL OR protein_g >= 0)
        AND (fat_g IS NULL OR fat_g >= 0)
        AND (fiber_g IS NULL OR fiber_g >= 0)
        AND (sodium_mg IS NULL OR sodium_mg >= 0)
        AND (calcium_mg IS NULL OR calcium_mg >= 0)
        AND (iron_mg IS NULL OR iron_mg >= 0)
        AND (potassium_mg IS NULL OR potassium_mg >= 0)
        AND (vitamin_a_mcg_rae IS NULL OR vitamin_a_mcg_rae >= 0)
        AND (vitamin_c_mg IS NULL OR vitamin_c_mg >= 0)
    ),
    CONSTRAINT chk_meal_items_source CHECK (source IN ('AI', 'USER_ADDED', 'USER_EDITED')),
    CONSTRAINT fk_meal_items_meal FOREIGN KEY (meal_id) REFERENCES meals (meal_id) ON DELETE CASCADE,
    CONSTRAINT fk_meal_items_food FOREIGN KEY (food_id) REFERENCES foods (food_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE meal_nutrition_summaries (
    meal_id CHAR(36) NOT NULL,
    calories_kcal DECIMAL(12,2) NULL,
    carbohydrate_g DECIMAL(12,2) NULL,
    protein_g DECIMAL(12,2) NULL,
    fat_g DECIMAL(12,2) NULL,
    fiber_g DECIMAL(12,2) NULL,
    sodium_mg DECIMAL(12,2) NULL,
    calcium_mg DECIMAL(12,2) NULL,
    iron_mg DECIMAL(12,2) NULL,
    potassium_mg DECIMAL(12,2) NULL,
    vitamin_a_mcg_rae DECIMAL(12,2) NULL,
    vitamin_c_mg DECIMAL(12,2) NULL,
    basis VARCHAR(24) NOT NULL DEFAULT 'AI_ESTIMATE',
    calculated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (meal_id),
    CONSTRAINT chk_meal_nutrition_basis CHECK (basis IN ('AI_ESTIMATE', 'USER_CONFIRMED', 'CATALOG')),
    CONSTRAINT chk_meal_nutrition_nonnegative CHECK (
        (calories_kcal IS NULL OR calories_kcal >= 0)
        AND (carbohydrate_g IS NULL OR carbohydrate_g >= 0)
        AND (protein_g IS NULL OR protein_g >= 0)
        AND (fat_g IS NULL OR fat_g >= 0)
        AND (fiber_g IS NULL OR fiber_g >= 0)
        AND (sodium_mg IS NULL OR sodium_mg >= 0)
        AND (calcium_mg IS NULL OR calcium_mg >= 0)
        AND (iron_mg IS NULL OR iron_mg >= 0)
        AND (potassium_mg IS NULL OR potassium_mg >= 0)
        AND (vitamin_a_mcg_rae IS NULL OR vitamin_a_mcg_rae >= 0)
        AND (vitamin_c_mg IS NULL OR vitamin_c_mg >= 0)
    ),
    CONSTRAINT fk_meal_nutrition_meal FOREIGN KEY (meal_id) REFERENCES meals (meal_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_analysis_runs (
    analysis_run_id CHAR(36) NOT NULL,
    meal_id CHAR(36) NOT NULL,
    run_type VARCHAR(24) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(30) NOT NULL DEFAULT 'OPENAI',
    model VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    provider_response_id VARCHAR(120) NULL,
    request_fingerprint CHAR(64) NOT NULL,
    attempt_no INT UNSIGNED NOT NULL DEFAULT 1,
    normalized_response JSON NULL,
    error_code VARCHAR(80) NULL,
    error_message VARCHAR(500) NULL,
    latency_ms INT UNSIGNED NULL,
    input_tokens INT UNSIGNED NULL,
    output_tokens INT UNSIGNED NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (analysis_run_id),
    UNIQUE KEY uk_ai_runs_attempt (meal_id, run_type, request_fingerprint, attempt_no),
    INDEX idx_ai_runs_meal_started (meal_id, started_at),
    CONSTRAINT chk_ai_runs_type CHECK (run_type IN ('INITIAL', 'REANALYSIS', 'RECOMMENDATION')),
    CONSTRAINT chk_ai_runs_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT')),
    CONSTRAINT fk_ai_runs_meal FOREIGN KEY (meal_id) REFERENCES meals (meal_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendations (
    recommendation_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    meal_id CHAR(36) NOT NULL,
    recommendation_date DATE NOT NULL COMMENT 'KST date',
    status VARCHAR(24) NOT NULL,
    target_nutrient VARCHAR(40) NULL,
    no_recommendation_reason VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (recommendation_id),
    UNIQUE KEY uk_recommendations_user_date (user_id, recommendation_date),
    UNIQUE KEY uk_recommendations_meal (meal_id),
    CONSTRAINT chk_recommendations_status CHECK (status IN ('CREATED', 'NO_CANDIDATE', 'SELECTED', 'SKIPPED')),
    CONSTRAINT chk_recommendations_no_candidate CHECK (
        (status = 'NO_CANDIDATE' AND no_recommendation_reason IS NOT NULL)
        OR
        (status <> 'NO_CANDIDATE' AND no_recommendation_reason IS NULL)
    ),
    CONSTRAINT fk_recommendations_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendations_meal_owner FOREIGN KEY (meal_id, user_id)
        REFERENCES meals (meal_id, user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_ingredients (
    ingredient_id CHAR(36) NOT NULL,
    recommendation_id CHAR(36) NOT NULL,
    food_id CHAR(36) NOT NULL,
    rank_order INT UNSIGNED NOT NULL,
    ingredient_name VARCHAR(200) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    nutrition_snapshot JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ingredient_id),
    UNIQUE KEY uk_recommendation_ingredients_rank (recommendation_id, rank_order),
    UNIQUE KEY uk_recommendation_ingredients_parent (recommendation_id, ingredient_id),
    INDEX idx_recommendation_ingredients_food (food_id),
    CONSTRAINT fk_recommendation_ingredients_recommendation FOREIGN KEY (recommendation_id) REFERENCES recommendations (recommendation_id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_ingredients_food FOREIGN KEY (food_id) REFERENCES foods (food_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_dishes (
    recommendation_dish_id CHAR(36) NOT NULL,
    ingredient_id CHAR(36) NOT NULL,
    food_id CHAR(36) NULL,
    dish_name VARCHAR(200) NOT NULL,
    rank_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (recommendation_dish_id),
    UNIQUE KEY uk_recommendation_dishes_rank (ingredient_id, rank_order),
    INDEX idx_recommendation_dishes_food (food_id),
    CONSTRAINT chk_recommendation_dishes_rank CHECK (rank_order BETWEEN 1 AND 3),
    CONSTRAINT fk_recommendation_dishes_ingredient FOREIGN KEY (ingredient_id) REFERENCES recommendation_ingredients (ingredient_id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_dishes_food FOREIGN KEY (food_id) REFERENCES foods (food_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Each recommendation ingredient should have 2-3 dish cards.
-- MySQL CHECK constraints cannot count child rows, so enforce the minimum of 2
-- in the recommendation creation transaction. rank_order limits the maximum to 3.

CREATE TABLE recommendation_decisions (
    recommendation_id CHAR(36) NOT NULL,
    ingredient_id CHAR(36) NULL,
    decision VARCHAR(20) NOT NULL,
    decided_at DATETIME(6) NOT NULL,
    PRIMARY KEY (recommendation_id),
    UNIQUE KEY uk_recommendation_decisions_ingredient (ingredient_id),
    CONSTRAINT chk_recommendation_decisions_value CHECK (
        (decision = 'SELECTED' AND ingredient_id IS NOT NULL)
        OR
        (decision = 'SKIPPED' AND ingredient_id IS NULL)
    ),
    CONSTRAINT fk_recommendation_decisions_recommendation FOREIGN KEY (recommendation_id) REFERENCES recommendations (recommendation_id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_decisions_ingredient FOREIGN KEY (recommendation_id, ingredient_id)
        REFERENCES recommendation_ingredients (recommendation_id, ingredient_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
