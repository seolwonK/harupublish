package com.haru.chat.domain;

public enum ChatRoomType {
    /** Student ↔ tutor one-to-one room. */
    DIRECT,
    /** Per-user read-only room that receives system notifications ("Haru 알림"). */
    SYSTEM_NOTICE,
    /** User ↔ operations team room ("운영팀"). */
    OPS
}
