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
) VALUES (
    'admin@admin.com',
    '$2a$10$XoLBCcQF/yinFfalwUJ7H.9MZSQSnDws1wbbkXp2fiiSS/gvRfTla',
    'Admin',
    NULL,
    'Asia/Seoul',
    'ADMIN',
    'ACTIVE',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
) ON DUPLICATE KEY UPDATE
    password_hash = '$2a$10$XoLBCcQF/yinFfalwUJ7H.9MZSQSnDws1wbbkXp2fiiSS/gvRfTla',
    name = 'Admin',
    time_zone = 'Asia/Seoul',
    active_role = 'ADMIN',
    account_status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP(6),
    id = LAST_INSERT_ID(id);

SET @admin_user_id = LAST_INSERT_ID();

INSERT IGNORE INTO user_roles (user_id, role) VALUES (@admin_user_id, 'STUDENT');
INSERT IGNORE INTO user_roles (user_id, role) VALUES (@admin_user_id, 'ADMIN');
