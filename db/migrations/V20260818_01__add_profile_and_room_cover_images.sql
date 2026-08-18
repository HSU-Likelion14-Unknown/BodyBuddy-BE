ALTER TABLE users
    ADD COLUMN profile_image_url VARCHAR(500) NULL
        AFTER share_to_room;

ALTER TABLE rooms
    ADD COLUMN cover_image_url VARCHAR(500) NULL
        AFTER user_id;