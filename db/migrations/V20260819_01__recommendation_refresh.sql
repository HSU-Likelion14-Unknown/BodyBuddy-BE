-- Preserve every replaced ingredient name so refreshes never repeat prior cards.
-- AI fallback ingredients may not exist in the food catalog, so food_id is nullable.

ALTER TABLE recommendations
    ADD COLUMN excluded_ingredient_names JSON NULL AFTER nutrient_gap,
    ADD COLUMN refresh_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER excluded_ingredient_names;

UPDATE recommendations
SET excluded_ingredient_names = JSON_ARRAY()
WHERE excluded_ingredient_names IS NULL;

ALTER TABLE recommendations
    MODIFY COLUMN excluded_ingredient_names JSON NOT NULL;

ALTER TABLE recommendation_ingredients
    MODIFY COLUMN food_id CHAR(36) NULL;
