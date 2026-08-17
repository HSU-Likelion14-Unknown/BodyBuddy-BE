-- Ensure anonymous users always have a non-null room sharing preference.
-- Existing development databases may already have this column from ddl-auto=update.

SET @share_to_room_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND column_name = 'share_to_room'
);
SET @ensure_share_to_room_sql = IF(
    @share_to_room_exists = 0,
    'ALTER TABLE users ADD COLUMN share_to_room BOOLEAN NULL DEFAULT FALSE AFTER onboarding_completed_at',
    'SELECT 1'
);
PREPARE ensure_share_to_room_statement FROM @ensure_share_to_room_sql;
EXECUTE ensure_share_to_room_statement;
DEALLOCATE PREPARE ensure_share_to_room_statement;

UPDATE users
SET share_to_room = FALSE
WHERE share_to_room IS NULL;

ALTER TABLE users
    MODIFY COLUMN share_to_room BOOLEAN NOT NULL DEFAULT FALSE;
