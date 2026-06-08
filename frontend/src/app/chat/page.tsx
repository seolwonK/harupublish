"use client";

import { Paperclip, Send } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { Fragment, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ChatContact,
  ChatMessageResponse,
  ChatRoomSummary,
  ChatSocketEvent,
  HaruApiError,
  haruApi,
  resolveAssetUrl
} from "../api";
import { useAuth } from "../auth";
import { ApiNotice, AppHeader, Avatar, Button } from "../components";
import { useChatSocket } from "./useChatSocket";

function formatConversationTime(value: string | null) {
  if (!value) return "";
  const date = new Date(value);
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  if (sameDay) {
    return date.toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" });
  }
  return date.toLocaleDateString("ko-KR", { month: "numeric", day: "numeric" });
}

function formatDateDivider(value: string) {
  return new Date(value).toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric", weekday: "short" });
}

function formatMessageTime(value: string) {
  return new Date(value).toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" });
}

function roomSubtitle(room: ChatRoomSummary | null, connected: boolean) {
  if (!room) return "";
  if (room.roomType === "SYSTEM_NOTICE") return "시스템 알림 · 읽기 전용";
  if (room.roomType === "OPS") return "운영팀 문의";
  return connected ? "실시간 연결됨" : "연결 대기 중";
}

function avatarLabel(name: string) {
  return name.trim().slice(0, 1).toUpperCase() || "H";
}

function errorMessage(error: unknown) {
  if (error instanceof HaruApiError) return error.message;
  return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

function MessageBubble({ message, mine }: { message: ChatMessageResponse; mine: boolean }) {
  const classes = `bubble ${mine ? "mine" : "other"}${message.messageType === "SYSTEM" ? " soft" : ""}`;

  if (message.messageType === "IMAGE" && message.attachmentUrl) {
    return (
      <div className={classes}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={resolveAssetUrl(message.attachmentUrl) ?? message.attachmentUrl}
          alt={message.attachmentName ?? "첨부 이미지"}
          style={{ maxWidth: "240px", borderRadius: "8px", display: "block" }}
        />
      </div>
    );
  }
  if (message.messageType === "FILE" && message.attachmentUrl) {
    return (
      <div className={classes}>
        <a href={resolveAssetUrl(message.attachmentUrl) ?? message.attachmentUrl} target="_blank" rel="noreferrer">
          📎 {message.attachmentName ?? "첨부 파일"}
        </a>
      </div>
    );
  }
  return <div className={classes}>{message.body}</div>;
}

function ChatPageContent() {
  const searchParams = useSearchParams();
  const { user, accessToken, loading, refreshSession } = useAuth();

  const [rooms, setRooms] = useState<ChatRoomSummary[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [search, setSearch] = useState("");
  const [draft, setDraft] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  // '새 대화' 검색: 상대를 직접 찾아 채팅 시작.
  const [composeOpen, setComposeOpen] = useState(false);
  const [contactQuery, setContactQuery] = useState("");
  const [contacts, setContacts] = useState<ChatContact[]>([]);
  const [contactsLoading, setContactsLoading] = useState(false);

  // 헤더 시계. SSR에서는 비워 두고 마운트 후에만 채워 hydration 불일치를 막는다.
  const [koreaTime, setKoreaTime] = useState<string | null>(null);

  // 상대 읽음 포인터의 실시간 갱신분 (READ 이벤트). 방 전환 시 초기화.
  const [liveCounterpartReadId, setLiveCounterpartReadId] = useState<number | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const messageAreaRef = useRef<HTMLDivElement | null>(null);
  const autoScrollRef = useRef(true);

  const selectedRoom = useMemo(
    () => rooms.find((room) => room.id === selectedRoomId) ?? null,
    [rooms, selectedRoomId]
  );

  const updateRoomPreview = useCallback((message: ChatMessageResponse) => {
    setRooms((current) => {
      const updated = current.map((room) =>
        room.id === message.chatRoomId
          ? {
              ...room,
              lastMessagePreview: message.body ?? message.attachmentName ?? "[파일]",
              lastMessageAt: message.createdAt
            }
          : room
      );
      // 마지막 메시지가 갱신된 방을 맨 위로 올린다.
      return [...updated].sort((a, b) => (b.lastMessageAt ?? "").localeCompare(a.lastMessageAt ?? ""));
    });
  }, []);

  const reloadRooms = useCallback(async () => {
    if (!accessToken) return;
    try {
      const list = await haruApi.listChats(accessToken);
      setRooms(list.rooms);
    } catch {
      // 목록 갱신 실패는 조용히 무시한다 (다음 이벤트에서 재시도).
    }
  }, [accessToken]);

  const handleRoomEvent = useCallback(
    (event: ChatSocketEvent) => {
      if (event.type === "READ") {
        // 상대가 읽음 포인터를 옮겼다 → 내 메시지 '읽음' 표시 갱신.
        if (event.userId !== user?.id && typeof event.lastReadMessageId === "number") {
          const readId = event.lastReadMessageId;
          setLiveCounterpartReadId((current) => (current === null || readId > current ? readId : current));
        }
        return;
      }
      if (event.type !== "MESSAGE" || !event.message) return;
      const message = event.message;
      setMessages((current) => (current.some((existing) => existing.id === message.id) ? current : [...current, message]));
      updateRoomPreview(message);
      // 보고 있는 방에 도착한 상대 메시지는 즉시 읽음 처리한다.
      if (message.senderUserId !== user?.id && accessToken) {
        void haruApi.markChatRead(accessToken, message.chatRoomId, message.id).catch(() => undefined);
      }
    },
    [accessToken, updateRoomPreview, user?.id]
  );

  const handleUnreadEvent = useCallback(
    (event: ChatSocketEvent) => {
      if (event.chatRoomId === selectedRoomId) return;
      const message = event.message;
      setRooms((current) => {
        // 목록에 없는 새 방이면 전체 목록을 다시 불러온다.
        if (!current.some((room) => room.id === event.chatRoomId)) {
          void reloadRooms();
          return current;
        }
        // 알고 있는 방이면 리페치 없이 미읽음 수와 미리보기만 제자리 갱신.
        const updated = current.map((room) =>
          room.id === event.chatRoomId
            ? {
                ...room,
                unreadCount: room.unreadCount + 1,
                lastMessagePreview: message ? (message.body ?? message.attachmentName ?? "[파일]") : room.lastMessagePreview,
                lastMessageAt: message?.createdAt ?? room.lastMessageAt
              }
            : room
        );
        return [...updated].sort((a, b) => (b.lastMessageAt ?? "").localeCompare(a.lastMessageAt ?? ""));
      });
    },
    [reloadRooms, selectedRoomId]
  );

  const { connected } = useChatSocket({
    accessToken,
    roomId: selectedRoomId,
    onRoomEvent: handleRoomEvent,
    onUnreadEvent: handleUnreadEvent,
    refreshToken: refreshSession
  });

  // 최초 진입: (튜터 상세에서 넘어온 경우) 방 생성 후 목록 로드.
  useEffect(() => {
    if (!accessToken) return;
    let cancelled = false;

    (async () => {
      try {
        let targetRoomId: number | null = null;
        const tutorIdParam = searchParams.get("tutorId")?.trim();
        if (tutorIdParam && /^\d+$/.test(tutorIdParam)) {
          const room = await haruApi.startChat(accessToken, Number(tutorIdParam));
          targetRoomId = room.id;
        }
        const list = await haruApi.listChats(accessToken);
        if (cancelled) return;
        setRooms(list.rooms);
        setSelectedRoomId((current) => current ?? targetRoomId ?? list.rooms[0]?.id ?? null);
      } catch (error) {
        if (!cancelled) setNotice(errorMessage(error));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [accessToken, searchParams]);

  // 방 선택: 메시지 로드 + 읽음 처리.
  useEffect(() => {
    if (!accessToken || selectedRoomId === null) return;
    let cancelled = false;
    setMessages([]);
    setHasMore(false);
    setLiveCounterpartReadId(null);
    autoScrollRef.current = true;

    haruApi
      .getChatMessages(accessToken, selectedRoomId)
      .then((response) => {
        if (cancelled) return;
        setMessages(response.messages);
        setHasMore(response.hasMore);
        const last = response.messages[response.messages.length - 1];
        if (last) {
          void haruApi.markChatRead(accessToken, selectedRoomId, last.id).catch(() => undefined);
        }
        setRooms((current) => current.map((room) => (room.id === selectedRoomId ? { ...room, unreadCount: 0 } : room)));
      })
      .catch((error) => {
        if (!cancelled) setNotice(errorMessage(error));
      });

    return () => {
      cancelled = true;
    };
  }, [accessToken, selectedRoomId]);

  // 새 메시지 도착 시 맨 아래로 스크롤 (이전 메시지 로드는 제외).
  useEffect(() => {
    if (!autoScrollRef.current) {
      autoScrollRef.current = true;
      return;
    }
    const area = messageAreaRef.current;
    if (area) area.scrollTop = area.scrollHeight;
  }, [messages]);

  // 헤더 시계: 마운트 후 Asia/Seoul 기준으로 30초마다 갱신.
  useEffect(() => {
    const update = () =>
      setKoreaTime(
        new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit", timeZone: "Asia/Seoul" })
      );
    update();
    const timer = setInterval(update, 30_000);
    return () => clearInterval(timer);
  }, []);

  // '새 대화' 상대 검색 (디바운스 300ms).
  useEffect(() => {
    if (!composeOpen || !accessToken) return;
    const query = contactQuery.trim();
    if (!query) {
      setContacts([]);
      setContactsLoading(false);
      return;
    }
    setContactsLoading(true);
    const timer = setTimeout(() => {
      haruApi
        .searchChatContacts(accessToken, query)
        .then((response) => setContacts(response.contacts))
        .catch(() => setContacts([]))
        .finally(() => setContactsLoading(false));
    }, 300);
    return () => clearTimeout(timer);
  }, [composeOpen, contactQuery, accessToken]);

  async function startChatWith(contact: ChatContact) {
    if (!accessToken) return;
    setNotice(null);
    try {
      const room = await haruApi.startChatWithUser(accessToken, contact.userId);
      await reloadRooms();
      setSelectedRoomId(room.id);
      setComposeOpen(false);
      setContactQuery("");
      setContacts([]);
    } catch (error) {
      setNotice(errorMessage(error));
    }
  }

  const filteredRooms = useMemo(() => {
    if (!search.trim()) return rooms;
    const query = search.trim().toLowerCase();
    return rooms.filter(
      (room) =>
        room.counterpartName.toLowerCase().includes(query) ||
        (room.lastMessagePreview ?? "").toLowerCase().includes(query)
    );
  }, [rooms, search]);

  // 상대 읽음 포인터 = 방 목록 스냅샷 vs 실시간 READ 이벤트 중 더 최신 값.
  const counterpartReadId = Math.max(liveCounterpartReadId ?? 0, selectedRoom?.counterpartLastReadMessageId ?? 0);

  // '읽음' 라벨은 상대가 읽은 내 메시지 중 마지막 하나에만 붙인다.
  const lastReadMineId = useMemo(() => {
    let last: number | null = null;
    for (const message of messages) {
      if (message.senderUserId === user?.id && message.id > 0 && message.id <= counterpartReadId) {
        last = message.id;
      }
    }
    return last;
  }, [messages, counterpartReadId, user?.id]);

  async function loadOlderMessages() {
    if (!accessToken || selectedRoomId === null || messages.length === 0) return;
    autoScrollRef.current = false;
    try {
      const response = await haruApi.getChatMessages(accessToken, selectedRoomId, { beforeId: messages[0].id });
      setMessages((current) => [...response.messages, ...current]);
      setHasMore(response.hasMore);
    } catch (error) {
      setNotice(errorMessage(error));
    }
  }

  async function sendMessage() {
    const content = draft.trim();
    if (!content || !accessToken || !selectedRoom || selectedRoom.roomType === "SYSTEM_NOTICE") return;

    setDraft("");
    setNotice(null);
    const tempId = -Date.now();
    const optimistic: ChatMessageResponse = {
      id: tempId,
      chatRoomId: selectedRoom.id,
      senderUserId: user?.id ?? null,
      senderName: user?.name ?? "나",
      messageType: "TEXT",
      body: content,
      attachmentUrl: null,
      attachmentName: null,
      attachmentContentType: null,
      attachmentSize: null,
      createdAt: new Date().toISOString()
    };
    setMessages((current) => [...current, optimistic]);

    try {
      const saved = await haruApi.sendChatMessage(accessToken, selectedRoom.id, content);
      setMessages((current) => {
        const withoutTemp = current.filter((message) => message.id !== tempId);
        return withoutTemp.some((message) => message.id === saved.id) ? withoutTemp : [...withoutTemp, saved];
      });
      updateRoomPreview(saved);
    } catch (error) {
      setMessages((current) => current.filter((message) => message.id !== tempId));
      setDraft(content);
      setNotice(errorMessage(error));
    }
  }

  async function handleFileSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !accessToken || !selectedRoom || selectedRoom.roomType === "SYSTEM_NOTICE") return;

    setUploading(true);
    setNotice(null);
    try {
      const saved = await haruApi.uploadChatAttachment(accessToken, selectedRoom.id, file);
      setMessages((current) => (current.some((message) => message.id === saved.id) ? current : [...current, saved]));
      updateRoomPreview(saved);
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setUploading(false);
    }
  }

  if (!loading && !accessToken) {
    return (
      <>
        <AppHeader />
        <main className="chat-layout">
          <section className="chat-empty">
            <ApiNotice type="info">채팅을 사용하려면 로그인이 필요합니다.</ApiNotice>
            <Button as="a" href="/login">
              로그인하러 가기
            </Button>
          </section>
        </main>
      </>
    );
  }

  const readOnlyRoom = selectedRoom?.roomType === "SYSTEM_NOTICE";

  return (
    <>
      <AppHeader />
      <main className="chat-layout">
        <aside className="chat-list">
          <button type="button" className="chat-new-button" onClick={() => setComposeOpen((open) => !open)}>
            {composeOpen ? "← 대화 목록" : "+ 새 대화"}
          </button>

          {composeOpen ? (
            <div className="chat-compose">
              <label className="search-box">
                <input
                  autoFocus
                  placeholder="이름 또는 이메일로 상대 검색"
                  value={contactQuery}
                  onChange={(event) => setContactQuery(event.target.value)}
                />
              </label>
              {contactsLoading ? <p className="chat-compose-hint">검색 중…</p> : null}
              {!contactsLoading && contactQuery.trim() && contacts.length === 0 ? (
                <p className="chat-compose-hint">검색 결과가 없습니다.</p>
              ) : null}
              {!contactQuery.trim() ? <p className="chat-compose-hint">이름이나 이메일로 상대를 검색하세요.</p> : null}
              {contacts.map((contact) => (
                <article className="conversation" key={contact.userId} onClick={() => void startChatWith(contact)}>
                  <Avatar label={avatarLabel(contact.name)} imageUrl={contact.imageUrl} />
                  <div>
                    <strong>{contact.name}</strong>
                    <p>{contact.tutor ? "튜터" : "학생"}</p>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <>
              <label className="search-box">
                <input placeholder="튜터, 메시지 검색" value={search} onChange={(event) => setSearch(event.target.value)} />
              </label>
              {filteredRooms.map((room) => (
                <article
                  className={room.id === selectedRoomId ? "conversation selected" : "conversation"}
                  key={room.id}
                  onClick={() => setSelectedRoomId(room.id)}
                >
                  <Avatar label={avatarLabel(room.counterpartName)} imageUrl={room.counterpartImageUrl} />
                  <div>
                    <strong>{room.counterpartName}</strong>
                    <p>{room.lastMessagePreview ?? "대화를 시작해 보세요."}</p>
                  </div>
                  <div className="conversation-meta">
                    <span>{formatConversationTime(room.lastMessageAt)}</span>
                    {room.unreadCount ? <b>{room.unreadCount}</b> : null}
                  </div>
                </article>
              ))}
              {filteredRooms.length === 0 ? (
                <ApiNotice type="info">대화가 없습니다. ‘+ 새 대화’로 상대를 검색해 보세요.</ApiNotice>
              ) : null}
            </>
          )}
        </aside>

        <section className="chat-room">
          <header className="chat-head">
            <Avatar label={avatarLabel(selectedRoom?.counterpartName ?? "H")} imageUrl={selectedRoom?.counterpartImageUrl} />
            <div>
              <h1>{selectedRoom?.counterpartName ?? "대화를 선택해 주세요"}</h1>
              <p>{roomSubtitle(selectedRoom, connected)}</p>
            </div>
            <span suppressHydrationWarning>{koreaTime ? `한국 시간 ${koreaTime}` : ""}</span>
          </header>
          {notice ? <ApiNotice type="error">{notice}</ApiNotice> : null}
          <div className="message-area" ref={messageAreaRef}>
            {hasMore ? (
              <button type="button" className="date-divider" style={{ cursor: "pointer", border: "none" }} onClick={loadOlderMessages}>
                이전 메시지 보기
              </button>
            ) : null}
            {messages.map((message, index) => {
              const previous = messages[index - 1];
              const showDivider = !previous || formatDateDivider(previous.createdAt) !== formatDateDivider(message.createdAt);
              const mine = message.senderUserId !== null && message.senderUserId === user?.id;

              return (
                <Fragment key={message.id}>
                  {showDivider ? <span className="date-divider">{formatDateDivider(message.createdAt)}</span> : null}
                  <div className={`message-row ${mine ? "mine" : "other"}`}>
                    <MessageBubble message={message} mine={mine} />
                    <div className="message-meta">
                      {mine && message.id === lastReadMineId ? <span className="message-read">읽음</span> : null}
                      <span className="message-time">{formatMessageTime(message.createdAt)}</span>
                    </div>
                  </div>
                </Fragment>
              );
            })}
          </div>
          <footer className="chat-input">
            <input
              placeholder={readOnlyRoom ? "이 채팅방은 읽기 전용입니다." : "메시지를 입력하세요..."}
              value={draft}
              disabled={readOnlyRoom || !selectedRoom}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  void sendMessage();
                }
              }}
            />
            <input ref={fileInputRef} type="file" hidden onChange={handleFileSelected} accept="image/*,.pdf,.txt,.zip,.doc,.docx,.xls,.xlsx,.ppt,.pptx" />
            <button
              type="button"
              aria-label="파일 첨부"
              style={{ background: "none", border: "none", cursor: readOnlyRoom ? "default" : "pointer", padding: 0, display: "flex" }}
              disabled={readOnlyRoom || uploading || !selectedRoom}
              onClick={() => fileInputRef.current?.click()}
            >
              <Paperclip size={18} aria-hidden />
            </button>
            <button
              className="send-button"
              aria-label="메시지 보내기"
              onClick={() => void sendMessage()}
              disabled={!draft.trim() || !selectedRoom || readOnlyRoom}
            >
              <Send size={19} />
            </button>
          </footer>
        </section>
      </main>

      <style>{`
        .chat-new-button {
          width: 100%;
          padding: 10px 12px;
          margin-bottom: 10px;
          border: 1px solid var(--line);
          border-radius: 10px;
          background: var(--brand-soft);
          color: var(--brand-dark);
          font-weight: 600;
          font-size: 14px;
          cursor: pointer;
        }
        .chat-new-button:hover { filter: brightness(0.98); }
        .chat-compose { display: flex; flex-direction: column; gap: 6px; }
        .chat-compose-hint { color: var(--muted); font-size: 13px; margin: 2px 6px; }
      `}</style>
    </>
  );
}

export default function ChatPage() {
  return (
    <Suspense fallback={null}>
      <ChatPageContent />
    </Suspense>
  );
}
