-- Create meal reaction storage for shared-room meals.
-- A user may select multiple different emojis for the same meal,
-- but the same emoji cannot be registered more than once.

CREATE TABLE meal_reactions (
    reaction_id CHAR(36) NOT NULL,
    meal_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    emoji_type VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (reaction_id),

    UNIQUE KEY uk_meal_reactions_meal_user_emoji (
        meal_id,
        user_id,
        emoji_type
        ),

    INDEX idx_meal_reactions_meal (meal_id),
    INDEX idx_meal_reactions_user (user_id),

    CONSTRAINT chk_meal_reactions_emoji CHECK (
        emoji_type IN (
            'FIRE',
            'HEART',
            'HEART_EYES',
            'LIKE',
            'THUMBS_UP',
            'THUMBS_DOWN',
            'CLAP',
            'PARTY',
            'STAR',
            'HUNDRED',
            'YUMMY',
            'DELICIOUS',
            'HUNGRY',
            'HEALTHY',
            'STRONG',
            'CHEER',
            'GOOD',
            'AMAZING',
            'WOW',
            'HAHA',
            'SMILE',
            'LAUGH',
            'CUTE',
            'SURPRISED',
            'SAD',
            'ANGRY'
            )
        ),

    CONSTRAINT fk_meal_reactions_meal
        FOREIGN KEY (meal_id)
            REFERENCES meals (meal_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_meal_reactions_user
        FOREIGN KEY (user_id)
            REFERENCES users (user_id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;