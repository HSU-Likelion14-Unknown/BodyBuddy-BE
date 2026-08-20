DELETE FROM meal_reactions;

ALTER TABLE meal_reactions
    DROP INDEX uk_meal_reactions_meal_user_emoji,
    ADD COLUMN room_id CHAR(36) NOT NULL AFTER reaction_id,
    ADD INDEX idx_meal_reactions_room_meal (
        room_id,
        meal_id
    ),
    ADD UNIQUE KEY uk_meal_reactions_room_meal_user_emoji (
        room_id,
        meal_id,
        user_id,
        emoji_type
    ),
    ADD CONSTRAINT fk_meal_reactions_room
        FOREIGN KEY (room_id)
        REFERENCES rooms (room_id)
        ON DELETE CASCADE;