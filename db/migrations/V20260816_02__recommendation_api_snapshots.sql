-- Recommendations are created once per meal, not once per user/day.
-- Persist nutrition inputs so a later meal does not change an existing response.

-- The baseline schema already has this key. Keep the migration safe for databases
-- created from an older snapshot where the meal-level unique key may be absent.
SET @meal_unique_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'recommendations'
      AND index_name = 'uk_recommendations_meal'
      AND non_unique = 0
);
SET @ensure_meal_unique_sql = IF(
    @meal_unique_exists = 0,
    'ALTER TABLE recommendations ADD UNIQUE KEY uk_recommendations_meal (meal_id)',
    'SELECT 1'
);
PREPARE ensure_meal_unique_statement FROM @ensure_meal_unique_sql;
EXECUTE ensure_meal_unique_statement;
DEALLOCATE PREPARE ensure_meal_unique_statement;

-- The old unique index may be supporting fk_recommendations_user. Create its
-- non-unique replacement first so MySQL can safely release the old index.
SET @user_date_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'recommendations'
      AND index_name = 'idx_recommendations_user_date'
      AND non_unique = 1
);
SET @ensure_user_date_index_sql = IF(
    @user_date_index_exists = 0,
    'ALTER TABLE recommendations ADD INDEX idx_recommendations_user_date (user_id, recommendation_date)',
    'SELECT 1'
);
PREPARE ensure_user_date_index_statement FROM @ensure_user_date_index_sql;
EXECUTE ensure_user_date_index_statement;
DEALLOCATE PREPARE ensure_user_date_index_statement;

SET @legacy_user_date_unique_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'recommendations'
      AND index_name = 'uk_recommendations_user_date'
      AND non_unique = 0
);
SET @drop_legacy_user_date_unique_sql = IF(
    @legacy_user_date_unique_exists > 0,
    'ALTER TABLE recommendations DROP INDEX uk_recommendations_user_date',
    'SELECT 1'
);
PREPARE drop_legacy_user_date_unique_statement FROM @drop_legacy_user_date_unique_sql;
EXECUTE drop_legacy_user_date_unique_statement;
DEALLOCATE PREPARE drop_legacy_user_date_unique_statement;

ALTER TABLE recommendations
    ADD COLUMN daily_nutrition JSON NULL AFTER no_recommendation_reason,
    ADD COLUMN nutrient_gap JSON NULL AFTER daily_nutrition;

UPDATE recommendations
SET daily_nutrition = JSON_OBJECT(),
    nutrient_gap = JSON_OBJECT(
        'proteinG', 0,
        'fiberG', 0,
        'calciumMg', 0,
        'ironMg', 0,
        'potassiumMg', 0,
        'vitaminAMcgRae', 0,
        'vitaminCMg', 0
    )
WHERE daily_nutrition IS NULL OR nutrient_gap IS NULL;

ALTER TABLE recommendations
    MODIFY COLUMN daily_nutrition JSON NOT NULL,
    MODIFY COLUMN nutrient_gap JSON NOT NULL;
