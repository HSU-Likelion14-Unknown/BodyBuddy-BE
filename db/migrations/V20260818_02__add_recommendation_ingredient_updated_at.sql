ALTER TABLE recommendation_ingredients
    ADD COLUMN updated_at DATETIME(6) NULL
        AFTER created_at;

UPDATE recommendation_ingredients
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE recommendation_ingredients
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;