/**
 * 신규 튜터 ↔ 신규 학생 채팅 E2E.
 * 흐름: 튜터 가입 → 튜터 전환/프로필 작성/제출 → admin 승인 → 학생 가입 →
 *       방 생성 → 양방향 STOMP 구독 → 양방향 메시지 송수신 + 읽음 확인.
 * 실행: node scripts/chat-e2e-fresh-pair.mjs
 */
import { Client } from "@stomp/stompjs";

const BASE = "http://localhost:8080";
const WS = "ws://localhost:8080/ws-chat";
const RUN = Date.now();

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

function connectStomp(name, token, roomId, onMessage) {
  const ready = waitFor(`${name} STOMP connect`);
  const client = new Client({
    brokerURL: WS,
    connectHeaders: { Authorization: `Bearer ${token}` },
    onConnect: () => {
      client.subscribe(`/topic/chat-rooms/${roomId}`, (frame) => onMessage(JSON.parse(frame.body)));
      ready.resolve();
    },
    onStompError: (frame) => console.error(`${name} STOMP error:`, frame.headers.message)
  });
  client.activate();
  return { client, ready: ready.promise };
}

// 1. 신규 튜터 생성 → 승인
const tutor = await api("/api/auth/signup", {
  method: "POST",
  body: { email: `fresh-tutor-${RUN}@example.com`, password: "password123!", name: "Fresh Tutor", timeZone: "Asia/Seoul" }
});
const tutorProfile = await api("/api/tutors/me/switch", { method: "POST", token: tutor.accessToken });
await api("/api/tutors/me/profile", {
  method: "PUT",
  token: tutor.accessToken,
  body: {
    displayName: "Fresh Pair Tutor",
    shortIntroduction: "E2E fresh pair test tutor.",
    aboutMe: "Testing chat between brand-new accounts.",
    whatIOffer: "Conversation practice.",
    category: "KOREAN",
    profileImageUrl: "https://example.com/profile.jpg",
    introVideoUrl: "https://youtu.be/haru-intro",
    thumbnailUrl: "https://example.com/thumb.jpg",
    availableLanguages: ["Korean"],
    lessonPrice25Amount: 20.0,
    lessonPrice50Amount: 38.0,
    availableTimeNote: "Anytime",
    paymentMethod: "BANK_TRANSFER"
  }
});
await api("/api/tutors/me/profile/submit", { method: "POST", token: tutor.accessToken });
console.log("✓ fresh tutor created, profileId:", tutorProfile.id);

const admin = await api("/api/auth/login", {
  method: "POST",
  body: { email: "admin@admin.com", password: "admin1234" }
});
await api(`/api/admin/tutors/${tutorProfile.id}/approve`, { method: "PATCH", token: admin.accessToken });
console.log("✓ tutor approved by admin");

// 2. 신규 학생 생성, 방 시작
const student = await api("/api/auth/signup", {
  method: "POST",
  body: { email: `fresh-student-${RUN}@example.com`, password: "password123!", name: "Fresh Student", timeZone: "Asia/Seoul" }
});
const room = await api("/api/chats", {
  method: "POST",
  token: student.accessToken,
  body: { tutorProfileId: tutorProfile.id }
});
console.log("✓ room created:", room.id, "| student sees counterpart:", room.counterpartName);

// 3. 양방향 STOMP 구독
const tutorGotStudentMsg = waitFor("tutor receives student message");
const studentGotTutorMsg = waitFor("student receives tutor message");

const tutorSock = connectStomp("tutor", tutor.accessToken, room.id, (event) => {
  if (event.type === "MESSAGE" && event.message.senderUserId === student.user.id) tutorGotStudentMsg.resolve(event);
});
const studentSock = connectStomp("student", student.accessToken, room.id, (event) => {
  if (event.type === "MESSAGE" && event.message.senderUserId === tutor.user.id) studentGotTutorMsg.resolve(event);
});
await Promise.all([tutorSock.ready, studentSock.ready]);
console.log("✓ both sides subscribed via STOMP");

// 4. 학생 → 튜터
await api(`/api/chats/${room.id}/messages`, {
  method: "POST",
  token: student.accessToken,
  body: { body: "튜터님 안녕하세요! 수업 가능할까요?" }
});
const toTutor = await tutorGotStudentMsg.promise;
console.log("✓ tutor received in realtime:", JSON.stringify(toTutor.message.body));

// 5. 튜터 → 학생
await api(`/api/chats/${room.id}/messages`, {
  method: "POST",
  token: tutor.accessToken,
  body: { body: "네! 환영합니다. 시간대 알려주세요 :)" }
});
const toStudent = await studentGotTutorMsg.promise;
console.log("✓ student received in realtime:", JSON.stringify(toStudent.message.body));

// 6. 튜터 채팅 목록에서 방/미읽음 확인 + 읽음 처리
const tutorRooms = await api("/api/chats", { token: tutor.accessToken });
const direct = tutorRooms.rooms.find((entry) => entry.id === room.id);
console.log("✓ tutor room list:", direct.counterpartName, "| unread:", direct.unreadCount, "| preview:", JSON.stringify(direct.lastMessagePreview));
if (direct.counterpartName !== "Fresh Student") throw new Error("Tutor should see the student name");

await api(`/api/chats/${room.id}/read`, {
  method: "POST",
  token: tutor.accessToken,
  body: { lastMessageId: toTutor.message.id }
});
const unread = await api("/api/chats/unread-count", { token: tutor.accessToken });
console.log("✓ tutor unread after read:", unread.count);

// 7. 메시지 히스토리 양쪽 시점 검증
const history = await api(`/api/chats/${room.id}/messages`, { token: student.accessToken });
console.log("✓ history:", history.messages.map((m) => `${m.senderName}: ${m.body}`));
if (history.messages.length !== 2) throw new Error("Expected 2 messages in history");

await tutorSock.client.deactivate();
await studentSock.client.deactivate();
console.log("\nFRESH PAIR CHAT E2E PASSED");
process.exit(0);
