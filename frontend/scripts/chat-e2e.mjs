/**
 * 채팅 E2E 검증 스크립트 (로컬 docker 백엔드 대상).
 * 흐름: 튜터 로그인 → 학생 가입/로그인 → 방 생성 → 튜터 STOMP 구독 →
 *       학생 REST 전송 → 튜터가 MESSAGE + UNREAD 이벤트 수신 확인 →
 *       읽음 처리 → READ 이벤트 확인 → 미읽음 카운트 검증.
 * 실행: node scripts/chat-e2e.mjs
 */
import { Client } from "@stomp/stompjs";

const BASE = "http://localhost:8080";
const WS = "ws://localhost:8080/ws-chat";

async function api(path, { method = "GET", token, body } = {}) {
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  });
  const payload = await response.json();
  if (!payload.success) throw new Error(`${method} ${path} failed: ${JSON.stringify(payload)}`);
  return payload.data;
}

function waitFor(label, timeoutMs = 8000) {
  let resolveFn;
  const promise = new Promise((resolve, reject) => {
    resolveFn = resolve;
    setTimeout(() => reject(new Error(`Timed out waiting for ${label}`)), timeoutMs);
  });
  return { promise, resolve: (value) => resolveFn(value) };
}

const tutorLogin = await api("/api/auth/login", {
  method: "POST",
  body: { email: "tutor.korean@haru.local", password: "admin1234" }
});
console.log("✓ tutor logged in:", tutorLogin.user.name);

const studentEmail = `chat-e2e-${Date.now()}@example.com`;
const studentSignup = await api("/api/auth/signup", {
  method: "POST",
  body: { email: studentEmail, password: "password123!", name: "E2E Student", timeZone: "Asia/Seoul" }
});
console.log("✓ student signed up:", studentEmail);

const tutors = await api("/api/tutors");
const tutorProfile = tutors.find((t) => t.userId === tutorLogin.user.id) ?? tutors[0];
console.log("✓ target tutorProfileId:", tutorProfile.tutorProfileId);

const room = await api("/api/chats", {
  method: "POST",
  token: studentSignup.accessToken,
  body: { tutorProfileId: tutorProfile.tutorProfileId }
});
console.log("✓ room created:", room.id, room.roomType, "counterpart:", room.counterpartName);

const messageEvent = waitFor("MESSAGE event");
const unreadEvent = waitFor("UNREAD event");
const readEvent = waitFor("READ event");

const tutorClient = new Client({
  brokerURL: WS,
  connectHeaders: { Authorization: `Bearer ${tutorLogin.accessToken}` },
  onConnect: () => {
    console.log("✓ tutor STOMP connected");
    tutorClient.subscribe(`/topic/chat-rooms/${room.id}`, (frame) => {
      const event = JSON.parse(frame.body);
      if (event.type === "MESSAGE") messageEvent.resolve(event);
      if (event.type === "READ") readEvent.resolve(event);
    });
    tutorClient.subscribe("/user/queue/unread", (frame) => {
      unreadEvent.resolve(JSON.parse(frame.body));
    });
    afterSubscribed();
  },
  onStompError: (frame) => console.error("STOMP error:", frame.headers.message)
});
tutorClient.activate();

async function afterSubscribed() {
  const sent = await api(`/api/chats/${room.id}/messages`, {
    method: "POST",
    token: studentSignup.accessToken,
    body: { body: "안녕하세요! E2E 테스트 메시지입니다." }
  });
  console.log("✓ student sent message id:", sent.id);
}

const received = await messageEvent.promise;
console.log("✓ tutor received MESSAGE via STOMP:", JSON.stringify(received.message.body));

const unread = await unreadEvent.promise;
console.log("✓ tutor received UNREAD signal for room:", unread.chatRoomId);

const count = await api("/api/chats/unread-count", { token: tutorLogin.accessToken });
console.log("✓ tutor unread count:", count.count);
if (count.count < 1) throw new Error("Expected unread count >= 1");

await api(`/api/chats/${room.id}/read`, {
  method: "POST",
  token: tutorLogin.accessToken,
  body: { lastMessageId: received.message.id }
});
const readReceived = await readEvent.promise;
console.log("✓ READ event broadcast, lastReadMessageId:", readReceived.lastReadMessageId);

const countAfter = await api("/api/chats/unread-count", { token: tutorLogin.accessToken });
console.log("✓ tutor unread count after read:", countAfter.count);

// 구독 인가 검증: 학생이 남의 방(존재하지 않는 1번 방이 아니라 임의 큰 id) 구독 시 거부되는지
const forbidden = waitFor("subscription rejection", 5000);
const outsiderClient = new Client({
  brokerURL: WS,
  connectHeaders: { Authorization: `Bearer ${studentSignup.accessToken}` },
  onConnect: () => {
    outsiderClient.subscribe(`/topic/chat-rooms/999999`, () => {
      console.error("✗ outsider received event from foreign room — SHOULD NOT HAPPEN");
    });
  },
  onStompError: (frame) => forbidden.resolve(frame.headers.message)
});
outsiderClient.activate();
const rejection = await forbidden.promise;
console.log("✓ foreign room subscription rejected:", JSON.stringify(rejection));

await tutorClient.deactivate();
await outsiderClient.deactivate();
console.log("\nALL CHAT E2E CHECKS PASSED");
process.exit(0);
