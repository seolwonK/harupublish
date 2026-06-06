package com.haru.chat.application;

import com.haru.chat.api.dto.ChatMessageResponse;
import com.haru.chat.api.dto.ChatSocketEvent;
import com.haru.chat.config.WebSocketConfig;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Publishes chat events to the STOMP broker. Publishing is deferred to after
 * the surrounding transaction commits so a rollback never leaks a broadcast.
 */
@Component
public class ChatBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Publish a new message to the room topic and ping the other participants' unread queues. */
    public void broadcastMessage(List<Long> participantUserIds, ChatMessageResponse message) {
        afterCommit(() -> {
            messagingTemplate.convertAndSend(
                    WebSocketConfig.ROOM_TOPIC_PREFIX + message.chatRoomId(),
                    ChatSocketEvent.message(message)
            );
            for (Long userId : participantUserIds) {
                if (!userId.equals(message.senderUserId())) {
                    messagingTemplate.convertAndSendToUser(
                            String.valueOf(userId),
                            WebSocketConfig.USER_UNREAD_QUEUE,
                            ChatSocketEvent.unread(message)
                    );
                }
            }
        });
    }

    /** Tell the room that a participant moved their read pointer. */
    public void broadcastRead(Long chatRoomId, Long userId, Long lastReadMessageId) {
        afterCommit(() -> messagingTemplate.convertAndSend(
                WebSocketConfig.ROOM_TOPIC_PREFIX + chatRoomId,
                ChatSocketEvent.read(chatRoomId, userId, lastReadMessageId)
        ));
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
