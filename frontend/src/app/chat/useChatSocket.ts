"use client";

import { Client } from "@stomp/stompjs";
import { useEffect, useRef, useState } from "react";
import { CHAT_WS_URL, ChatSocketEvent } from "../api";

type UseChatSocketOptions = {
  accessToken: string | null;
  /** 현재 보고 있는 채팅방. null이면 방 구독 없이 유저 큐만 구독한다. */
  roomId: number | null;
  /** /topic/chat-rooms/{roomId} 이벤트 (MESSAGE, READ). */
  onRoomEvent: (event: ChatSocketEvent) => void;
  /** /user/queue/unread 이벤트 — 다른 방에 새 메시지가 도착했다는 신호. */
  onUnreadEvent: (event: ChatSocketEvent) => void;
  /** STOMP 인증 실패 시 새 access token을 반환하면 그 토큰으로 재연결한다. */
  refreshToken?: () => Promise<string | null>;
};

/**
 * 채팅 STOMP 연결 훅. 전송은 REST(POST /api/chats/...)로 하고 이 소켓은 수신
 * 전용이다. 끊기면 5초 간격으로 자동 재연결하고, 토큰 만료로 거부되면
 * refreshToken으로 재발급받아 재시도한다.
 */
export function useChatSocket({ accessToken, roomId, onRoomEvent, onUnreadEvent, refreshToken }: UseChatSocketOptions) {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const onRoomEventRef = useRef(onRoomEvent);
  const onUnreadEventRef = useRef(onUnreadEvent);
  const refreshTokenRef = useRef(refreshToken);

  onRoomEventRef.current = onRoomEvent;
  onUnreadEventRef.current = onUnreadEvent;
  refreshTokenRef.current = refreshToken;

  useEffect(() => {
    if (!accessToken) return;

    const client = new Client({
      brokerURL: CHAT_WS_URL,
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/user/queue/unread", (frame) => {
          try {
            onUnreadEventRef.current(JSON.parse(frame.body) as ChatSocketEvent);
          } catch {
            // 형식이 깨진 이벤트는 무시한다.
          }
        });
      },
      onStompError: () => {
        // CONNECT 거부 = 토큰 만료 가능성. 재발급 후 다음 재연결에 반영한다.
        void refreshTokenRef.current?.().then((nextToken) => {
          if (nextToken) {
            client.connectHeaders = { Authorization: `Bearer ${nextToken}` };
          }
        });
      },
      onWebSocketClose: () => setConnected(false)
    });

    client.activate();
    clientRef.current = client;

    return () => {
      clientRef.current = null;
      setConnected(false);
      void client.deactivate();
    };
  }, [accessToken]);

  useEffect(() => {
    const client = clientRef.current;
    if (!connected || !client || roomId === null) return;

    const subscription = client.subscribe(`/topic/chat-rooms/${roomId}`, (frame) => {
      try {
        onRoomEventRef.current(JSON.parse(frame.body) as ChatSocketEvent);
      } catch {
        // 형식이 깨진 이벤트는 무시한다.
      }
    });
    return () => {
      try {
        subscription.unsubscribe();
      } catch {
        // 이미 연결이 끊긴 경우 무시한다.
      }
    };
  }, [connected, roomId]);

  return { connected };
}
