ALTER TABLE meal_items
    ADD COLUMN nutrition_basis VARCHAR(24) NULL AFTER vitamin_c_mg,
    ADD COLUMN nutrition_provider VARCHAR(30) NULL AFTER nutrition_basis,
    ADD COLUMN nutrition_model VARCHAR(100) NULL AFTER nutrition_provider,
    ADD COLUMN nutrition_prompt_version VARCHAR(40) NULL AFTER nutrition_model,
    ADD COLUMN nutrition_confidence DECIMAL(5,4) NULL AFTER nutrition_prompt_version,
    ADD CONSTRAINT chk_meal_items_nutrition_basis CHECK (
        nutrition_basis IS NULL OR nutrition_basis IN ('AI_ESTIMATE', 'USER_CONFIRMED', 'CATALOG')
    ),
    ADD CONSTRAINT chk_meal_items_nutrition_confidence CHECK (
        nutrition_confidence IS NULL OR nutrition_confidence BETWEEN 0 AND 1
    );
