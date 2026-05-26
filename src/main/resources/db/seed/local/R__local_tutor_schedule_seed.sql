INSERT INTO users (
    email,
    password_hash,
    name,
    mobile_number,
    time_zone,
    active_role,
    account_status,
    created_at,
    updated_at
) VALUES
    (
        'tutor.korean@haru.local',
        '$2a$10$XoLBCcQF/yinFfalwUJ7H.9MZSQSnDws1wbbkXp2fiiSS/gvRfTla',
        'Kim Hana',
        NULL,
        'Asia/Seoul',
        'TUTOR',
        'ACTIVE',
        CURRENT_TIMESTAMP(6),
        CURRENT_TIMESTAMP(6)
    ),
    (
        'tutor.kpop@haru.local',
        '$2a$10$XoLBCcQF/yinFfalwUJ7H.9MZSQSnDws1wbbkXp2fiiSS/gvRfTla',
        'Park Minjun',
        NULL,
        'Asia/Seoul',
        'TUTOR',
        'ACTIVE',
        CURRENT_TIMESTAMP(6),
        CURRENT_TIMESTAMP(6)
    ),
    (
        'tutor.beauty@haru.local',
        '$2a$10$XoLBCcQF/yinFfalwUJ7H.9MZSQSnDws1wbbkXp2fiiSS/gvRfTla',
        'Lee Sora',
        NULL,
        'Asia/Seoul',
        'TUTOR',
        'ACTIVE',
        CURRENT_TIMESTAMP(6),
        CURRENT_TIMESTAMP(6)
    ),
    (
        'tutor.travel@haru.local',
        '$2a$10$XoLBCcQF/yinFfalwUJ7H.9MZSQSnDws1wbbkXp2fiiSS/gvRfTla',
        'Choi Yujin',
        NULL,
        'Asia/Seoul',
        'TUTOR',
        'ACTIVE',
        CURRENT_TIMESTAMP(6),
        CURRENT_TIMESTAMP(6)
    )
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    name = VALUES(name),
    time_zone = VALUES(time_zone),
    active_role = VALUES(active_role),
    account_status = VALUES(account_status),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT IGNORE INTO user_roles (user_id, role)
SELECT id, 'STUDENT'
FROM users
WHERE email IN (
    'tutor.korean@haru.local',
    'tutor.kpop@haru.local',
    'tutor.beauty@haru.local',
    'tutor.travel@haru.local'
);

INSERT IGNORE INTO user_roles (user_id, role)
SELECT id, 'TUTOR'
FROM users
WHERE email IN (
    'tutor.korean@haru.local',
    'tutor.kpop@haru.local',
    'tutor.beauty@haru.local',
    'tutor.travel@haru.local'
);

INSERT INTO tutor_profiles (
    user_id,
    display_name,
    short_introduction,
    about_me,
    what_i_offer,
    category,
    profile_image_url,
    intro_video_url,
    thumbnail_url,
    available_languages,
    lesson_price_25_amount,
    lesson_price_50_amount,
    available_time_note,
    payment_method,
    status,
    submitted_at,
    approved_at,
    created_at,
    updated_at
)
SELECT
    u.id,
    'Hana Korean Tutor',
    'Conversation-focused Korean lessons for daily life.',
    'I help beginners build confidence with practical Korean speaking practice.',
    'Pronunciation checks, survival Korean, and friendly conversation drills.',
    'KOREAN',
    '',
    'https://www.youtube.com/watch?v=jNQXAC9IVRw',
    '',
    'Korean,English',
    18000.00,
    34000.00,
    'Weekday evenings and Saturday morning KST.',
    'BANK_TRANSFER',
    'APPROVED',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM users u
WHERE u.email = 'tutor.korean@haru.local'
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    short_introduction = VALUES(short_introduction),
    about_me = VALUES(about_me),
    what_i_offer = VALUES(what_i_offer),
    category = VALUES(category),
    profile_image_url = VALUES(profile_image_url),
    intro_video_url = VALUES(intro_video_url),
    thumbnail_url = VALUES(thumbnail_url),
    available_languages = VALUES(available_languages),
    lesson_price_25_amount = VALUES(lesson_price_25_amount),
    lesson_price_50_amount = VALUES(lesson_price_50_amount),
    available_time_note = VALUES(available_time_note),
    payment_method = VALUES(payment_method),
    status = VALUES(status),
    approved_at = CURRENT_TIMESTAMP(6),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO tutor_profiles (
    user_id,
    display_name,
    short_introduction,
    about_me,
    what_i_offer,
    category,
    profile_image_url,
    intro_video_url,
    thumbnail_url,
    available_languages,
    lesson_price_25_amount,
    lesson_price_50_amount,
    available_time_note,
    payment_method,
    status,
    submitted_at,
    approved_at,
    created_at,
    updated_at
)
SELECT
    u.id,
    'Minjun K-pop Coach',
    'Learn Korean through K-pop lyrics and fan conversations.',
    'I combine language coaching with current K-pop culture context.',
    'Lyric reading, fan event phrases, and natural casual expressions.',
    'KPOP',
    '',
    NULL,
    '',
    'Korean,English,Japanese',
    22000.00,
    42000.00,
    'Afternoons and late evenings KST.',
    'BANK_TRANSFER',
    'APPROVED',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM users u
WHERE u.email = 'tutor.kpop@haru.local'
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    short_introduction = VALUES(short_introduction),
    about_me = VALUES(about_me),
    what_i_offer = VALUES(what_i_offer),
    category = VALUES(category),
    profile_image_url = VALUES(profile_image_url),
    thumbnail_url = VALUES(thumbnail_url),
    available_languages = VALUES(available_languages),
    lesson_price_25_amount = VALUES(lesson_price_25_amount),
    lesson_price_50_amount = VALUES(lesson_price_50_amount),
    available_time_note = VALUES(available_time_note),
    payment_method = VALUES(payment_method),
    status = VALUES(status),
    approved_at = CURRENT_TIMESTAMP(6),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO tutor_profiles (
    user_id,
    display_name,
    short_introduction,
    about_me,
    what_i_offer,
    category,
    profile_image_url,
    intro_video_url,
    thumbnail_url,
    available_languages,
    lesson_price_25_amount,
    lesson_price_50_amount,
    available_time_note,
    payment_method,
    status,
    submitted_at,
    approved_at,
    created_at,
    updated_at
)
SELECT
    u.id,
    'Sora K-beauty Guide',
    'K-beauty Korean lessons for shopping and skincare routines.',
    'I teach useful Korean for beauty stores, salons, and product reviews.',
    'Product vocabulary, consultation phrases, and routine explanations.',
    'KBEAUTY',
    '',
    NULL,
    '',
    'Korean,English,Chinese',
    20000.00,
    38000.00,
    'Morning and lunch slots KST.',
    'BANK_TRANSFER',
    'APPROVED',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM users u
WHERE u.email = 'tutor.beauty@haru.local'
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    short_introduction = VALUES(short_introduction),
    about_me = VALUES(about_me),
    what_i_offer = VALUES(what_i_offer),
    category = VALUES(category),
    profile_image_url = VALUES(profile_image_url),
    thumbnail_url = VALUES(thumbnail_url),
    available_languages = VALUES(available_languages),
    lesson_price_25_amount = VALUES(lesson_price_25_amount),
    lesson_price_50_amount = VALUES(lesson_price_50_amount),
    available_time_note = VALUES(available_time_note),
    payment_method = VALUES(payment_method),
    status = VALUES(status),
    approved_at = CURRENT_TIMESTAMP(6),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO tutor_profiles (
    user_id,
    display_name,
    short_introduction,
    about_me,
    what_i_offer,
    category,
    profile_image_url,
    intro_video_url,
    thumbnail_url,
    available_languages,
    lesson_price_25_amount,
    lesson_price_50_amount,
    available_time_note,
    payment_method,
    status,
    submitted_at,
    approved_at,
    created_at,
    updated_at
)
SELECT
    u.id,
    'Yujin Seoul Local',
    'Korean practice for travel, food, and local culture.',
    'I help travelers speak naturally in restaurants, cafes, and neighborhoods.',
    'Travel phrases, menu reading, etiquette, and local recommendations.',
    'OTHER',
    '',
    NULL,
    '',
    'Korean,English',
    17000.00,
    32000.00,
    'Flexible weekend schedule KST.',
    'BANK_TRANSFER',
    'APPROVED',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM users u
WHERE u.email = 'tutor.travel@haru.local'
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    short_introduction = VALUES(short_introduction),
    about_me = VALUES(about_me),
    what_i_offer = VALUES(what_i_offer),
    category = VALUES(category),
    profile_image_url = VALUES(profile_image_url),
    thumbnail_url = VALUES(thumbnail_url),
    available_languages = VALUES(available_languages),
    lesson_price_25_amount = VALUES(lesson_price_25_amount),
    lesson_price_50_amount = VALUES(lesson_price_50_amount),
    available_time_note = VALUES(available_time_note),
    payment_method = VALUES(payment_method),
    status = VALUES(status),
    approved_at = CURRENT_TIMESTAMP(6),
    updated_at = CURRENT_TIMESTAMP(6);

INSERT IGNORE INTO tutor_schedule_slots (
    tutor_profile_id,
    start_at,
    end_at,
    created_at,
    updated_at
)
SELECT
    tp.id,
    slots.start_at,
    DATE_ADD(slots.start_at, INTERVAL 30 MINUTE),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM tutor_profiles tp
JOIN users u ON u.id = tp.user_id
JOIN (
    SELECT 'tutor.korean@haru.local' AS email, TIMESTAMP '2026-05-27 10:00:00' AS start_at UNION ALL
    SELECT 'tutor.korean@haru.local', TIMESTAMP '2026-05-27 10:30:00' UNION ALL
    SELECT 'tutor.korean@haru.local', TIMESTAMP '2026-05-28 11:00:00' UNION ALL
    SELECT 'tutor.korean@haru.local', TIMESTAMP '2026-05-29 12:30:00' UNION ALL
    SELECT 'tutor.korean@haru.local', TIMESTAMP '2026-05-30 01:00:00' UNION ALL
    SELECT 'tutor.korean@haru.local', TIMESTAMP '2026-06-01 09:00:00' UNION ALL
    SELECT 'tutor.kpop@haru.local', TIMESTAMP '2026-05-27 06:00:00' UNION ALL
    SELECT 'tutor.kpop@haru.local', TIMESTAMP '2026-05-27 06:30:00' UNION ALL
    SELECT 'tutor.kpop@haru.local', TIMESTAMP '2026-05-28 13:00:00' UNION ALL
    SELECT 'tutor.kpop@haru.local', TIMESTAMP '2026-05-29 14:00:00' UNION ALL
    SELECT 'tutor.kpop@haru.local', TIMESTAMP '2026-05-30 11:30:00' UNION ALL
    SELECT 'tutor.kpop@haru.local', TIMESTAMP '2026-06-01 12:00:00' UNION ALL
    SELECT 'tutor.beauty@haru.local', TIMESTAMP '2026-05-27 02:00:00' UNION ALL
    SELECT 'tutor.beauty@haru.local', TIMESTAMP '2026-05-27 02:30:00' UNION ALL
    SELECT 'tutor.beauty@haru.local', TIMESTAMP '2026-05-28 03:00:00' UNION ALL
    SELECT 'tutor.beauty@haru.local', TIMESTAMP '2026-05-29 04:00:00' UNION ALL
    SELECT 'tutor.beauty@haru.local', TIMESTAMP '2026-05-30 05:00:00' UNION ALL
    SELECT 'tutor.beauty@haru.local', TIMESTAMP '2026-06-01 03:30:00' UNION ALL
    SELECT 'tutor.travel@haru.local', TIMESTAMP '2026-05-27 12:00:00' UNION ALL
    SELECT 'tutor.travel@haru.local', TIMESTAMP '2026-05-27 12:30:00' UNION ALL
    SELECT 'tutor.travel@haru.local', TIMESTAMP '2026-05-28 09:30:00' UNION ALL
    SELECT 'tutor.travel@haru.local', TIMESTAMP '2026-05-29 10:30:00' UNION ALL
    SELECT 'tutor.travel@haru.local', TIMESTAMP '2026-05-30 06:00:00' UNION ALL
    SELECT 'tutor.travel@haru.local', TIMESTAMP '2026-06-01 06:30:00'
) slots ON slots.email = u.email;

INSERT INTO reviews (
    booking_id,
    tutor_profile_id,
    student_user_id,
    reviewer_name,
    rating,
    body,
    visible,
    created_at,
    updated_at
)
SELECT
    NULL,
    tp.id,
    NULL,
    seed.reviewer_name,
    seed.rating,
    seed.body,
    TRUE,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM tutor_profiles tp
JOIN users u ON u.id = tp.user_id
JOIN (
    SELECT 'tutor.korean@haru.local' AS email, 'Lucas' AS reviewer_name, 5 AS rating, 'Hana makes Korean speaking feel natural and manageable.' AS body UNION ALL
    SELECT 'tutor.korean@haru.local', 'Mia', 5, 'The pronunciation corrections were specific and easy to practice.' UNION ALL
    SELECT 'tutor.kpop@haru.local', 'Sophie', 5, 'Learning through K-pop lyrics made vocabulary much easier to remember.' UNION ALL
    SELECT 'tutor.kpop@haru.local', 'Noah', 4, 'Minjun explained fan culture and casual Korean expressions clearly.' UNION ALL
    SELECT 'tutor.beauty@haru.local', 'Ava', 5, 'Sora taught useful phrases for skincare shopping and consultations.' UNION ALL
    SELECT 'tutor.travel@haru.local', 'Tom', 5, 'Yujin helped me prepare restaurant and cafe phrases for Seoul.'
) seed ON seed.email = u.email
WHERE NOT EXISTS (
    SELECT 1
    FROM reviews existing
    WHERE existing.tutor_profile_id = tp.id
      AND existing.reviewer_name = seed.reviewer_name
      AND existing.body = seed.body
);
