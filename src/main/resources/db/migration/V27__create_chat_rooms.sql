CREATE TABLE chat_rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_type VARCHAR(20) NOT NULL,
    direct_pair_key VARCHAR(50),
    last_message_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_rooms_pair_key UNIQUE (direct_pair_key)
);

CREATE TABLE chat_room_participants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_participant UNIQUE (chat_room_id, user_id),
    CONSTRAINT fk_chat_participant_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_rooms (id),
    CONSTRAINT fk_chat_participant_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_chat_participant_user ON chat_room_participants (user_id);
