-- Curated source data for mapping nutrient-ranked ingredients to safe dish candidates.
-- recommendation_dishes remains an immutable result snapshot table.

CREATE TABLE dish_templates (
    dish_id CHAR(36) NOT NULL,
    food_id CHAR(36) NULL,
    dish_name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    ingredient_names JSON NOT NULL COMMENT 'Normalized safety-review ingredients',
    allergen_codes JSON NOT NULL COMMENT 'Standardized allergen codes',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (dish_id),
    UNIQUE KEY uk_dish_templates_normalized_name (normalized_name),
    INDEX idx_dish_templates_food (food_id),
    INDEX idx_dish_templates_active (active),
    CONSTRAINT chk_dish_templates_ingredients_json CHECK (JSON_TYPE(ingredient_names) = 'ARRAY'),
    CONSTRAINT chk_dish_templates_allergens_json CHECK (JSON_TYPE(allergen_codes) = 'ARRAY'),
    CONSTRAINT fk_dish_templates_food FOREIGN KEY (food_id) REFERENCES foods (food_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ingredient_dish_mappings (
    mapping_id CHAR(36) NOT NULL,
    ingredient_food_id CHAR(36) NOT NULL,
    dish_id CHAR(36) NOT NULL,
    priority INT UNSIGNED NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (mapping_id),
    UNIQUE KEY uk_ingredient_dish_mapping (ingredient_food_id, dish_id),
    UNIQUE KEY uk_ingredient_dish_priority (ingredient_food_id, priority),
    INDEX idx_ingredient_dish_active (ingredient_food_id, active, priority),
    CONSTRAINT chk_ingredient_dish_priority CHECK (priority > 0),
    CONSTRAINT fk_ingredient_dish_ingredient FOREIGN KEY (ingredient_food_id) REFERENCES foods (food_id) ON DELETE CASCADE,
    CONSTRAINT fk_ingredient_dish_dish FOREIGN KEY (dish_id) REFERENCES dish_templates (dish_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
