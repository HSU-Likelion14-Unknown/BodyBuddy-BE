UPDATE users
SET profile_image_url = REPLACE(
        profile_image_url,
        '//app/uploads/',
        '/uploads/'
                        )
WHERE user_id IS NOT NULL
  AND profile_image_url LIKE '//app/uploads/%';

UPDATE rooms
SET cover_image_url = REPLACE(
        cover_image_url,
        '//app/uploads/',
        '/uploads/'
                      )
WHERE room_id IS NOT NULL
  AND cover_image_url LIKE '//app/uploads/%';