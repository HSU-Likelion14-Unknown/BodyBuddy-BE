-- Recommendations are created once per meal, not once per user/day.
-- Persist nutrition inputs so a later meal does not change an existing response.

ALTER TABLE recommendations
    DROP INDEX uk_recommendations_user_date,
    ADD INDEX idx_recommendations_user_date (user_id, recommendation_date),
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
