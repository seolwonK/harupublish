import { Paperclip } from "lucide-react";
import { AppHeader, Avatar, SearchBox, SendButton } from "../components";
import { conversations } from "../data";

export default function ChatPage() {
  return (
    <>
      <AppHeader />
      <main className="chat-layout">
        <aside className="chat-list">
          <SearchBox />
          {conversations.map((chat, index) => (
            <article className={index === 0 ? "conversation selected" : "conversation"} key={chat.name}>
              <Avatar label={chat.avatar} />
              <div>
                <strong>{chat.name}</strong>
                <p>{chat.message}</p>
              </div>
              <div className="conversation-meta">
                <span>{chat.time}</span>
                {chat.unread ? <b>{chat.unread}</b> : null}
              </div>
            </article>
          ))}
        </aside>

        <section className="chat-room">
          <header className="chat-head">
            <Avatar label="JH" />
            <div>
              <h1>김지현 튜터</h1>
              <p>온라인</p>
            </div>
            <span>한국 시간 10:32</span>
          </header>
          <div className="message-area">
            <span className="date-divider">2024년 5월 22일 (수)</span>
            <div className="bubble mine">수업이 30분 후 시작됩니다.</div>
            <div className="bubble other">오늘 수업 교재 미리 보내드려요!</div>
            <div className="bubble mine soft">수업 일정이 변경되었습니다. 새로운 시간: 5월 24일 (금) 오후 2:00</div>
            <div className="bubble other">네, 확인했어요!</div>
          </div>
          <footer className="chat-input">
            <input placeholder="메시지를 입력하세요..." />
            <Paperclip size={18} />
            <SendButton />
          </footer>
        </section>
      </main>
    </>
  );
}
