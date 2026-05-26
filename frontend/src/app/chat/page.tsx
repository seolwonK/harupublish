"use client";

import { Paperclip, Send } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import { ApiNotice, AppHeader, Avatar } from "../components";

type ChatMessage = {
  id: string;
  body: string;
  sender: "me" | "other";
  muted?: boolean;
  sentAt: string;
};

type ChatConversation = {
  id: string;
  name: string;
  avatar: string;
  online: boolean;
  unread: number;
  messages: ChatMessage[];
};

const initialConversations: ChatConversation[] = [
  {
    id: "tutor-jh",
    name: "김지현 튜터",
    avatar: "JH",
    online: true,
    unread: 2,
    messages: [
      { id: "jh-1", body: "수업이 30분 후 시작됩니다.", sender: "me", sentAt: "2026-05-22T10:02:00+09:00" },
      { id: "jh-2", body: "오늘 수업 교재 미리 보내드려요!", sender: "other", sentAt: "2026-05-22T10:05:00+09:00" },
      { id: "jh-3", body: "수업 일정이 변경되었습니다. 새로운 시간: 5월 24일 (금) 오후 2:00", sender: "me", muted: true, sentAt: "2026-05-22T10:09:00+09:00" },
      { id: "jh-4", body: "네, 확인했어요!", sender: "other", sentAt: "2026-05-22T10:10:00+09:00" }
    ]
  },
  {
    id: "tutor-pjh",
    name: "박준호 튜터",
    avatar: "JH",
    online: false,
    unread: 0,
    messages: [{ id: "pjh-1", body: "감사합니다 :)", sender: "other", sentAt: "2026-05-21T18:05:00+09:00" }]
  },
  {
    id: "tutor-cis",
    name: "최인서 튜터",
    avatar: "IS",
    online: false,
    unread: 0,
    messages: [{ id: "cis-1", body: "수업 자료 보내드릴게요.", sender: "other", sentAt: "2026-05-21T12:40:00+09:00" }]
  },
  {
    id: "system-haru",
    name: "Haru 알림",
    avatar: "H",
    online: false,
    unread: 1,
    messages: [{ id: "haru-1", body: "수업이 30분 후 시작됩니다.", sender: "other", sentAt: "2026-05-22T09:30:00+09:00" }]
  },
  {
    id: "ops-team",
    name: "운영팀",
    avatar: "OP",
    online: false,
    unread: 0,
    messages: [{ id: "ops-1", body: "문의하신 내용을 확인했습니다.", sender: "other", sentAt: "2026-05-19T16:05:00+09:00" }]
  }
];

function formatConversationTime(value: string) {
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

function messagePreview(conversation: ChatConversation) {
  return conversation.messages[conversation.messages.length - 1]?.body ?? "대화를 시작해 보세요.";
}

function createMessage(body: string, sender: ChatMessage["sender"], muted = false): ChatMessage {
  return {
    id: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`,
    body,
    sender,
    muted,
    sentAt: new Date().toISOString()
  };
}

function ChatPageContent() {
  const searchParams = useSearchParams();
  const [search, setSearch] = useState("");
  const [draft, setDraft] = useState("");
  const [notice, setNotice] = useState<string | null>("현재 채팅은 로컬 optimistic 미리보기입니다. 새로고침하면 기본 대화로 돌아갑니다.");
  const [conversations, setConversations] = useState<ChatConversation[]>(initialConversations);
  const [selectedConversationId, setSelectedConversationId] = useState(initialConversations[0].id);

  useEffect(() => {
    const tutorName = searchParams.get("name")?.trim();
    if (!tutorName) return;

    const tutorId = searchParams.get("tutorId")?.trim() || tutorName;
    const conversationId = `tutor-${tutorId}`;

    setConversations((current) => {
      if (current.some((conversation) => conversation.id === conversationId)) {
        return current;
      }

      return [
        {
          id: conversationId,
          name: tutorName,
          avatar: tutorName.slice(0, 1).toUpperCase(),
          online: true,
          unread: 0,
          messages: [createMessage(`${tutorName}와 새 대화를 시작했습니다. 첫 메시지를 보내보세요.`, "other")]
        },
        ...current
      ];
    });
    setSelectedConversationId(conversationId);
  }, [searchParams]);

  const filteredConversations = useMemo(() => {
    if (!search.trim()) return conversations;
    const query = search.trim().toLowerCase();
    return conversations.filter((conversation) => {
      return conversation.name.toLowerCase().includes(query)
        || messagePreview(conversation).toLowerCase().includes(query);
    });
  }, [conversations, search]);

  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === selectedConversationId) ?? conversations[0] ?? null,
    [conversations, selectedConversationId]
  );

  function selectConversation(conversationId: string) {
    setSelectedConversationId(conversationId);
    setConversations((current) => current.map((conversation) => {
      if (conversation.id !== conversationId) return conversation;
      return { ...conversation, unread: 0 };
    }));
  }

  function sendMessage() {
    const content = draft.trim();
    if (!content || !selectedConversation) return;

    const outgoingMessage = createMessage(content, "me");
    setConversations((current) => current.map((conversation) => {
      if (conversation.id !== selectedConversation.id) return conversation;
      return {
        ...conversation,
        unread: 0,
        messages: [...conversation.messages, outgoingMessage]
      };
    }));
    setDraft("");
    setNotice("메시지를 화면에 바로 반영했습니다. 현재 단계에서는 브라우저 로컬 상태에만 유지됩니다.");
  }

  return (
    <>
      <AppHeader />
      <main className="chat-layout">
        <aside className="chat-list">
          <label className="search-box">
            <input placeholder="튜터, 주제, 언어 검색" value={search} onChange={(event) => setSearch(event.target.value)} />
          </label>
          {filteredConversations.map((chat) => (
            <article className={chat.id === selectedConversation?.id ? "conversation selected" : "conversation"} key={chat.id} onClick={() => selectConversation(chat.id)}>
              <Avatar label={chat.avatar} />
              <div>
                <strong>{chat.name}</strong>
                <p>{messagePreview(chat)}</p>
              </div>
              <div className="conversation-meta">
                <span>{formatConversationTime(chat.messages[chat.messages.length - 1]?.sentAt ?? new Date().toISOString())}</span>
                {chat.unread ? <b>{chat.unread}</b> : null}
              </div>
            </article>
          ))}
          {filteredConversations.length === 0 ? <ApiNotice type="info">검색 조건에 맞는 대화가 없습니다.</ApiNotice> : null}
        </aside>

        <section className="chat-room">
          <header className="chat-head">
            <Avatar label={selectedConversation?.avatar ?? "H"} />
            <div>
              <h1>{selectedConversation?.name ?? "대화를 선택해 주세요"}</h1>
              <p>{selectedConversation?.online ? "온라인" : "오프라인"}</p>
            </div>
            <span>한국 시간 {new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })}</span>
          </header>
          {notice ? <ApiNotice type="info">{notice}</ApiNotice> : null}
          <div className="message-area">
            {selectedConversation?.messages.map((message, index) => {
              const previous = selectedConversation.messages[index - 1];
              const showDivider = !previous || formatDateDivider(previous.sentAt) !== formatDateDivider(message.sentAt);

              return (
                <div key={message.id}>
                  {showDivider ? <span className="date-divider">{formatDateDivider(message.sentAt)}</span> : null}
                  <div className={`bubble ${message.sender === "me" ? "mine" : "other"}${message.muted ? " soft" : ""}`}>{message.body}</div>
                </div>
              );
            })}
          </div>
          <footer className="chat-input">
            <input
              placeholder="메시지를 입력하세요..."
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  sendMessage();
                }
              }}
            />
            <Paperclip size={18} aria-hidden />
            <button className="send-button" aria-label="메시지 보내기" onClick={sendMessage} disabled={!draft.trim() || !selectedConversation}>
              <Send size={19} />
            </button>
          </footer>
        </section>
      </main>
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
