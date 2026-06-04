CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT NOT NULL,
    sender_user_id BIGINT,
    message_type VARCHAR(20) NOT NULL,
    body VARCHAR(2000),
    attachment_url VARCHAR(500),
    attachment_name VARCHAR(255),
    attachment_content_type VARCHAR(100),
    attachment_size BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_message_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_rooms (id),
    CONSTRAINT fk_chat_message_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id)
);

CREATE INDEX idx_chat_message_room_id ON chat_messages (chat_room_id, id);
