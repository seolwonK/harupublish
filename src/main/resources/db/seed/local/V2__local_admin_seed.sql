INSERT INTO users (
    id,
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
    1,
    'admin@haru.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiJg7D/Cl4u49Ua5bJEy0as/SIB0NUu',
    'Haru Admin',
    NULL,
    'Asia/Seoul',
    'ADMIN',
    'ACTIVE',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);

INSERT INTO user_roles (user_id, role) VALUES (1, 'STUDENT');
INSERT INTO user_roles (user_id, role) VALUES (1, 'ADMIN');
