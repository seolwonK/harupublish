export type Role = "STUDENT" | "TUTOR" | "ADMIN";
export type AccountStatus = "ACTIVE" | "INACTIVE" | "LOCKED";
export type TutorProfileStatus = "DRAFT" | "PENDING" | "APPROVED" | "REJECTED";
export type TutorCategory = "KOREAN" | "KPOP" | "KBEAUTY" | "OTHER";
export type BookingStatus = "RESERVED" | "CANCELLED" | "COMPLETED";
export type PaymentStatus = "PENDING" | "PAID" | "FAILED" | "CANCELLED" | "REFUND_REQUESTED" | "REFUNDED";
export type PaymentMethod = "CARD" | "PAYPAL" | "SIMPLE_PAY" | "LEMON_SQUEEZY";

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  message: string | null;
};

export type ApiErrorResponse = {
  success: false;
  error: {
    code: string;
    message: string;
  };
};

export type UserMeResponse = {
  id: number;
  email: string;
  name: string;
  mobileNumber: string | null;
  roles: Role[];
  activeRole: Role;
  accountStatus: AccountStatus;
  timeZone: string;
  lastLoginAt: string | null;
  tutorProfileStatus: TutorProfileStatus | null;
};

export type AuthTokenResponse = {
  accessToken: string;
  refreshToken: string;
  user: UserMeResponse;
};

export type ExpertListResponse = {
  tutorProfileId: number;
  userId: number;
  displayName: string | null;
  shortIntroduction: string | null;
  category: TutorCategory | null;
  profileImageUrl: string | null;
  thumbnailUrl: string | null;
  availableLanguages: string[] | null;
  lessonPrice25Amount: number | string | null;
  lessonPrice50Amount: number | string | null;
  /** 카탈로그 학생 표시가 (= lessonPrice25Amount * (1 + studentFeeRate), HALF_UP scale2). 표시 전용, 권위 가격은 checkout. */
  studentPrice25Amount: number | string | null;
  averageRating: number | null;
  reviewCount: number | null;
};

export type TutorProfileResponse = {
  id: number;
  userId: number;
  displayName: string | null;
  shortIntroduction: string | null;
  aboutMe: string | null;
  whatIOffer: string | null;
  category: TutorCategory | null;
  profileImageUrl: string | null;
  introVideoUrl: string | null;
  thumbnailUrl: string | null;
  availableLanguages: string[] | null;
  lessonPrice25Amount: number | string | null;
  lessonPrice50Amount: number | string | null;
  /** 학생 표시가 (= lessonPrice25Amount * (1 + studentFeeRate), HALF_UP scale2). 표시 전용, 권위 가격은 checkout. */
  studentPrice25Amount: number | string | null;
  availableTimeNote: string | null;
  paymentMethod: string | null;
  status: TutorProfileStatus;
  submittedAt: string | null;
  approvedAt: string | null;
  rejectedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type TutorProfileRequest = {
  displayName: string;
  shortIntroduction: string;
  aboutMe: string;
  whatIOffer: string;
  category: TutorCategory;
  profileImageUrl: string;
  introVideoUrl: string;
  thumbnailUrl: string;
  availableLanguages: string[];
  lessonPrice25Amount: number;
  lessonPrice50Amount: number;
  availableTimeNote: string;
  paymentMethod: string;
};

export type ScheduleSlotResponse = {
  id: number;
  startAt: string;
  endAt: string;
  booked: boolean;
};

export type TutorScheduleResponse = {
  slots: ScheduleSlotResponse[];
};

export type BookingResponse = {
  id: number;
  studentUserId: number;
  studentName: string | null;
  tutorProfileId: number;
  tutorDisplayName: string | null;
  tutorShortIntroduction: string | null;
  tutorImageUrl: string | null;
  scheduleSlotId: number;
  lessonDurationMinutes: number;
  startAt: string;
  endAt: string;
  status: BookingStatus;
  cancelReason: string | null;
  joinAvailable: boolean;
};

export type BookingListResponse = {
  bookings: BookingResponse[];
};

export type BookingParticipant = "student" | "tutor";

export type BookingJoinResponse = {
  bookingId: number;
  joinAvailable: boolean;
  joinUrl: string | null;
  message: string;
  provider: string | null;
  domain: string | null;
  roomName: string | null;
  jwt: string | null;
  expiresAt: string | null;
};

export type ReviewResponse = {
  id: number;
  bookingId: number | null;
  tutorProfileId: number;
  studentUserId: number | null;
  reviewerName: string;
  rating: number;
  body: string;
  createdAt: string;
};

export type ReviewListResponse = {
  tutorProfileId: number;
  averageRating: number;
  reviewCount: number;
  reviews: ReviewResponse[];
};

export type PaymentResponse = {
  id: number;
  studentUserId: number;
  tutorProfileId: number;
  lessonDurationMinutes: number;
  lessonPackCount: number;
  unitAmount: number | string;
  subtotalAmount: number | string;
  discountAmount: number | string;
  studentFeeAmount: number | string;
  totalAmount: number | string;
  currency: string;
  /** 결제 표시 통화 스냅샷 (모델1: 진실원장 USD, 표시 파생). */
  displayCurrency: string | null;
  /** 적용된 학생 마크업 요율 스냅샷 (예: 0.10). "10% 포함" 캡션 산출에 사용. */
  appliedStudentFeeRate: number | string | null;
  /** 결제 시점 USD->displayCurrency 환율 스냅샷 (없으면 null). */
  fxRateUsed: number | string | null;
  paymentMethod: PaymentMethod;
  status: PaymentStatus;
  refundReason: string | null;
  provider: string | null;
  providerCheckoutId: string | null;
  checkoutUrl: string | null;
  providerOrderId: string | null;
  providerOrderIdentifier: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PaymentListResponse = {
  payments: PaymentResponse[];
};

/** 관리자 환불 승인 큐: REFUND_REQUESTED 상태 결제 1건 (GET /api/admin/payments/refund-requests). */
export type RefundRequestResponse = {
  id: number;
  studentUserId: number;
  tutorProfileId: number;
  totalAmount: number | string;
  currency: string;
  refundReason: string | null;
  createdAt: string;
};

// ── 머니 코어: 플랫폼 설정 / 프로모 / 정산 / 인출 / 크레딧 / 환율 계약 (docs §8 기준) ──

export type PlatformSettingsResponse = {
  id: number;
  studentFeeRate: number | string;
  platformFeeRate: number | string;
  fivePackDiscountRate: number | string;
  tenPackDiscountRate: number | string;
  withdrawalFeeRatePaypal: number | string;
  withdrawalFeeRatePayoneer: number | string;
  withdrawalFeeRateDomestic: number | string;
  /** 프로모 면제 강사도 차감되는 고정 인출 수수료율 (예: 0.05). */
  promoWithdrawalFeeRate: number | string;
  cancellationCutoffHours: number;
  creditExpiryMonths: number;
  updatedAt: string;
  updatedBy: number | null;
};

export type UpdatePlatformSettingsRequest = {
  studentFeeRate: number;
  platformFeeRate: number;
  fivePackDiscountRate: number;
  tenPackDiscountRate: number;
  withdrawalFeeRatePaypal: number;
  withdrawalFeeRatePayoneer: number;
  withdrawalFeeRateDomestic: number;
  promoWithdrawalFeeRate: number;
  cancellationCutoffHours: number;
  creditExpiryMonths: number;
};

export type PromoWaiverResponse = {
  id: number;
  tutorProfileId: number;
  granted: boolean;
  /** 프로모 면제 종료일 (예: 2026-12-31). */
  waiverUntil: string | null;
  grantedAt: string | null;
  revokedAt: string | null;
  note: string | null;
};

export type TutorEarningsResponse = {
  tutorProfileId: number;
  currency: string;
  /** 적립된 사이버머니 총액 (USD 액면, 15% 차감 후). */
  totalEarnedAmount: number | string;
  /** 인출 HOLD/완료로 차감된 누적액. */
  totalWithdrawnAmount: number | string;
  /** 인출 가능한 현재 잔액. */
  availableBalanceAmount: number | string;
  /** 인출 진행 중(HOLD) 금액. */
  pendingWithdrawalAmount: number | string;
  ledger: TutorEarningLedgerEntry[];
};

export type TutorEarningLedgerEntry = {
  id: number;
  bookingId: number | null;
  withdrawalId: number | null;
  entryType: string;
  amount: number | string;
  balanceAfter: number | string;
  currency: string;
  memo: string | null;
  createdAt: string;
};

export type WithdrawalMethod = "PAYPAL" | "PAYONEER" | "DOMESTIC_BANK";
export type WithdrawalStatus = "REQUESTED" | "APPROVED" | "PAID" | "REJECTED" | "REVERSED";

export type WithdrawalResponse = {
  id: number;
  tutorProfileId: number;
  method: WithdrawalMethod;
  /** 인출 요청 총액 (USD 액면). */
  requestedAmount: number | string;
  /** 적용 인출 수수료율 (PayPal/Payoneer 0.03, 국내 0.05). */
  feeRate: number | string;
  feeAmount: number | string;
  /** 실제 지급액 = requestedAmount - feeAmount. */
  netAmount: number | string;
  currency: string;
  status: WithdrawalStatus;
  payoutAccount: string | null;
  rejectReason: string | null;
  promoPaybackPending: boolean;
  requestedAt: string;
  approvedAt: string | null;
  paidAt: string | null;
  rejectedAt: string | null;
};

export type CreateWithdrawalRequest = {
  method: WithdrawalMethod;
  amount: number;
  payoutAccount: string;
};

export type WithdrawalListResponse = {
  withdrawals: WithdrawalResponse[];
};

export type SettlementStatus = "OPEN" | "CLOSED" | "FINALIZED" | "PAID";

export type MonthlySettlementResponse = {
  id: number;
  tutorProfileId: number;
  year: number;
  month: number;
  /** 튜터 gross 합계 (= discounted 합). */
  grossAmount: number | string;
  /** 플랫폼 수수료 합계 (15%). */
  platformFeeAmount: number | string;
  /** 튜터 순수익 합계 (85%). */
  netAmount: number | string;
  lessonCount: number;
  currency: string;
  status: SettlementStatus;
  createdAt: string;
  updatedAt: string;
};

export type SettlementListResponse = {
  settlements: MonthlySettlementResponse[];
};

export type UpdateSettlementStatusRequest = {
  status: SettlementStatus;
};

export type HaruCreditLedgerEntry = {
  id: number;
  entryType: string;
  amount: number | string;
  balanceAfter: number | string;
  currency: string;
  paymentId: number | null;
  memo: string | null;
  createdAt: string;
};

export type HaruCreditResponse = {
  accountId: number;
  studentUserId: number;
  currency: string;
  /** 사용 가능한 크레딧 잔액 (USD 고정). */
  balanceAmount: number | string;
  /** 계정 12개월 비활성 기준 만료 예정일. */
  expiresAt: string | null;
  ledger: HaruCreditLedgerEntry[];
};

export type ExchangeRateResponse = {
  base: string;
  quote: string;
  rate: number | string;
  source: string | null;
  capturedAt: string;
};

export type SessionTokens = {
  accessToken: string;
  refreshToken: string;
};

// ── 채팅 ──

export type ChatRoomType = "DIRECT" | "SYSTEM_NOTICE" | "OPS";
export type ChatMessageType = "TEXT" | "IMAGE" | "FILE" | "SYSTEM";

export type ChatRoomSummary = {
  id: number;
  roomType: ChatRoomType;
  counterpartUserId: number | null;
  counterpartName: string;
  counterpartImageUrl: string | null;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
  /** 상대가 어디까지 읽었는지 — 내 메시지 '읽음' 표시용 (시스템 방은 null). */
  counterpartLastReadMessageId: number | null;
};

export type ChatRoomListResponse = {
  rooms: ChatRoomSummary[];
};

export type ChatMessageResponse = {
  id: number;
  chatRoomId: number;
  senderUserId: number | null;
  senderName: string;
  messageType: ChatMessageType;
  body: string | null;
  attachmentUrl: string | null;
  attachmentName: string | null;
  attachmentContentType: string | null;
  attachmentSize: number | null;
  createdAt: string;
};

export type ChatMessageListResponse = {
  messages: ChatMessageResponse[];
  hasMore: boolean;
};

/** 채팅 상대 검색 결과 1건. */
export type ChatContact = {
  userId: number;
  name: string;
  imageUrl: string | null;
  tutor: boolean;
};

export type ContactListResponse = {
  contacts: ChatContact[];
};

/** STOMP 토픽으로 수신하는 이벤트 envelope. */
export type ChatSocketEvent = {
  type: "MESSAGE" | "READ" | "UNREAD";
  chatRoomId: number;
  message?: ChatMessageResponse;
  userId?: number;
  lastReadMessageId?: number;
};

// ── 개발자 도구 (dev tools) — 백엔드 haru.dev.enabled=true 일 때만 동작 ──

export type DevTestAccount = {
  userId: number;
  email: string;
  /** 평문 비밀번호 (테스트 로그인용, dev 전용). */
  password: string;
  name: string;
  activeRole: Role;
};

export type DevCreatedSlot = {
  scheduleSlotId: number;
  startAt: string;
  endAt: string;
};

export type DevCreatedLesson = {
  bookingId: number;
  scheduleSlotId: number;
  startAt: string;
  endAt: string;
  status: string | null;
};

export type DevCreateTutorResult = {
  tutorProfileId: number;
  tutorAccount: DevTestAccount;
  /** 예약 수업을 만든 경우 그 수업의 소유 학생 (없으면 null). */
  studentAccount: DevTestAccount | null;
  openSlots: DevCreatedSlot[];
  bookedLessons: DevCreatedLesson[];
};

export type DevCreateTutorBody = {
  name?: string;
  email?: string;
  password?: string;
  category?: TutorCategory;
  lessonPrice25Amount?: number;
  lessonPrice50Amount?: number;
  availableLanguages?: string[];
  /** 공개(예약 가능) 슬롯 시작 시각들 (ISO instant). */
  availabilitySlots?: string[];
  /** 예약 수업 시작 시각들 (ISO instant). 지정 시 autoLessonCount보다 우선. */
  bookedLessons?: string[];
  /** bookedLessons 미지정 시 내일부터 30분 간격으로 자동 생성할 예약 수업 수. */
  autoLessonCount?: number;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const API_ORIGIN = new URL(API_BASE_URL).origin;
/** STOMP WebSocket 엔드포인트 (백엔드 /ws-chat). */
export const CHAT_WS_URL = `${API_ORIGIN.replace(/^http/, "ws")}/ws-chat`;
const REQUEST_TIMEOUT_MS = 8000;
const PUBLIC_CACHE_TTL_MS = 60_000;

export class HaruApiError extends Error {
  code: string;
  status: number;

  constructor(message: string, code: string, status: number) {
    super(message);
    this.name = "HaruApiError";
    this.code = code;
    this.status = status;
  }
}

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  token?: string | null;
  query?: Record<string, string | number | boolean | null | undefined>;
  cache?: RequestCache;
};

type CachedValue<T> = {
  expiresAt: number;
  promise: Promise<T>;
};

const publicRequestCache = new Map<string, CachedValue<unknown>>();

function cachedPublicRequest<T>(key: string, request: () => Promise<T>, ttlMs = PUBLIC_CACHE_TTL_MS): Promise<T> {
  const now = Date.now();
  const cached = publicRequestCache.get(key) as CachedValue<T> | undefined;
  if (cached && cached.expiresAt > now) {
    return cached.promise;
  }

  const promise = request().catch((error) => {
    publicRequestCache.delete(key);
    throw error;
  });
  publicRequestCache.set(key, { expiresAt: now + ttlMs, promise });
  return promise;
}

async function parseApiPayload<T>(response: Response): Promise<T> {
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | ApiErrorResponse | null;

  if (!response.ok || !payload?.success) {
    const error = payload && "error" in payload ? payload.error : null;
    throw new HaruApiError(error?.message ?? "API 요청에 실패했습니다.", error?.code ?? "HTTP_ERROR", response.status);
  }

  return payload.data;
}

function buildUrl(path: string, query?: RequestOptions["query"]) {
  const url = new URL(path, API_BASE_URL);
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== null && value !== undefined) {
      url.searchParams.set(key, String(value));
    }
  });
  return url.toString();
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response: Response;
  const controller = new AbortController();
  const timeoutId = globalThis.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? "GET",
      cache: options.cache,
      headers: {
        ...(options.body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {})
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal
    });
  } catch {
    throw new HaruApiError(
      "백엔드 서버에 연결할 수 없습니다. Spring Boot 서버가 http://localhost:8080 에서 실행 중인지 확인해주세요.",
      "NETWORK_ERROR",
      0
    );
  } finally {
    globalThis.clearTimeout(timeoutId);
  }

  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | ApiErrorResponse | null;

  if (!response.ok || !payload?.success) {
    const error = payload && "error" in payload ? payload.error : null;
    throw new HaruApiError(error?.message ?? "API 요청에 실패했습니다.", error?.code ?? "HTTP_ERROR", response.status);
  }

  return payload.data;
}

export async function apiUpload<T>(path: string, token: string, formData: FormData): Promise<T> {
  let response: Response;

  try {
    response = await fetch(buildUrl(path), {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`
      },
      body: formData
    });
  } catch {
    throw new HaruApiError(
      "백엔드 서버에 연결할 수 없습니다. Spring Boot 서버가 http://localhost:8080 에서 실행 중인지 확인해주세요.",
      "NETWORK_ERROR",
      0
    );
  }

  return parseApiPayload<T>(response);
}

export function resolveAssetUrl(value: string | null | undefined) {
  if (!value) return null;
  if (value.startsWith("/")) return `${API_ORIGIN}${value}`;
  return value;
}

export function youtubeEmbedUrl(value: string | null | undefined) {
  if (!value?.trim()) return null;

  try {
    const url = new URL(value.trim());
    const host = url.hostname.replace(/^www\./, "");
    let videoId: string | null = null;

    if (host === "youtu.be") {
      videoId = url.pathname.split("/").filter(Boolean)[0] ?? null;
    } else if (host === "youtube.com" || host === "m.youtube.com" || host === "youtube-nocookie.com") {
      if (url.pathname === "/watch") videoId = url.searchParams.get("v");
      if (url.pathname.startsWith("/shorts/") || url.pathname.startsWith("/embed/")) {
        videoId = url.pathname.split("/").filter(Boolean)[1] ?? null;
      }
    }

    if (!videoId || !/^[a-zA-Z0-9_-]{6,20}$/.test(videoId)) return null;
    return `https://www.youtube.com/embed/${videoId}`;
  } catch {
    return null;
  }
}

export function isYoutubeUrl(value: string | null | undefined) {
  if (!value?.trim()) return true;
  return youtubeEmbedUrl(value) !== null;
}

export const haruApi = {
  signup(body: { email: string; password: string; name: string; timeZone: string }) {
    return apiRequest<AuthTokenResponse>("/api/auth/signup", { method: "POST", body });
  },
  login(body: { email: string; password: string }) {
    return apiRequest<AuthTokenResponse>("/api/auth/login", { method: "POST", body });
  },
  refresh(refreshToken: string) {
    return apiRequest<AuthTokenResponse>("/api/auth/refresh", { method: "POST", body: { refreshToken } });
  },
  logout(refreshToken: string) {
    return apiRequest<void>("/api/auth/logout", { method: "POST", body: { refreshToken } });
  },
  me(token: string) {
    return apiRequest<UserMeResponse>("/api/auth/me", { token });
  },
  updateMe(token: string, body: { name: string; mobileNumber: string; timeZone: string }) {
    return apiRequest<UserMeResponse>("/api/users/me", { method: "PATCH", token, body });
  },
  changeActiveRole(token: string, activeRole: Role) {
    return apiRequest<UserMeResponse>("/api/users/me/active-role", { method: "PATCH", token, body: { activeRole } });
  },
  getTutors() {
    return cachedPublicRequest("tutors", () => apiRequest<ExpertListResponse[]>("/api/tutors"));
  },
  getTutor(id: number) {
    return cachedPublicRequest(`tutor:${id}`, () => apiRequest<TutorProfileResponse>(`/api/tutors/${id}`));
  },
  switchToTutor(token: string) {
    return apiRequest<TutorProfileResponse>("/api/tutors/me/switch", { method: "POST", token });
  },
  getMyTutorProfile(token: string) {
    return apiRequest<TutorProfileResponse>("/api/tutors/me/profile", { token });
  },
  updateMyTutorProfile(token: string, body: TutorProfileRequest) {
    return apiRequest<TutorProfileResponse>("/api/tutors/me/profile", { method: "PUT", token, body });
  },
  uploadTutorProfileImage(token: string, file: File) {
    const formData = new FormData();
    formData.append("file", file);
    return apiUpload<{ url: string }>("/api/tutors/me/profile/images", token, formData);
  },
  submitMyTutorProfile(token: string) {
    return apiRequest<TutorProfileResponse>("/api/tutors/me/profile/submit", { method: "POST", token });
  },
  approveTutor(token: string, tutorProfileId: number) {
    return apiRequest<TutorProfileResponse>(`/api/admin/tutors/${tutorProfileId}/approve`, { method: "PATCH", token });
  },
  rejectTutor(token: string, tutorProfileId: number) {
    return apiRequest<TutorProfileResponse>(`/api/admin/tutors/${tutorProfileId}/reject`, { method: "PATCH", token });
  },
  getPendingTutors(token: string) {
    return apiRequest<TutorProfileResponse[]>("/api/admin/tutors/pending", { token });
  },
  getPublicSchedule(tutorProfileId: number, from: string, to: string) {
    return apiRequest<TutorScheduleResponse>(`/api/tutors/${tutorProfileId}/schedule`, { query: { from, to }, cache: "no-store" });
  },
  getMySchedule(token: string, from: string, to: string) {
    return apiRequest<TutorScheduleResponse>("/api/tutors/me/schedule", { token, query: { from, to } });
  },
  replaceMySchedule(token: string, slots: { startAt: string }[]) {
    return apiRequest<TutorScheduleResponse>("/api/tutors/me/schedule", { method: "PUT", token, body: { slots } });
  },
  createBooking(token: string, body: { tutorProfileId: number; scheduleSlotId: number; lessonDurationMinutes: number }) {
    return apiRequest<BookingResponse>("/api/bookings", { method: "POST", token, body });
  },
  getMyBookings(token: string, participant: BookingParticipant = "student") {
    return apiRequest<BookingListResponse>("/api/bookings/me", { token, query: { participant } });
  },
  getBooking(token: string, bookingId: number) {
    return apiRequest<BookingResponse>(`/api/bookings/${bookingId}`, { token });
  },
  cancelBooking(token: string, bookingId: number, reason: string) {
    return apiRequest<BookingResponse>(`/api/bookings/${bookingId}/cancel`, { method: "PATCH", token, body: { reason } });
  },
  joinBooking(token: string, bookingId: number) {
    return apiRequest<BookingJoinResponse>(`/api/bookings/${bookingId}/join`, { token });
  },
  createReview(token: string, bookingId: number, body: { rating: number; body: string }) {
    return apiRequest<ReviewResponse>(`/api/bookings/${bookingId}/reviews`, { method: "POST", token, body });
  },
  getTutorReviews(tutorProfileId: number) {
    return cachedPublicRequest(`reviews:${tutorProfileId}`, () => apiRequest<ReviewListResponse>(`/api/tutors/${tutorProfileId}/reviews`));
  },
  createCheckout(token: string, body: { tutorProfileId: number; lessonDurationMinutes: number; lessonPackCount: number; paymentMethod: PaymentMethod }) {
    return apiRequest<PaymentResponse>("/api/payments/checkout", { method: "POST", token, body });
  },
  getMyPayments(token: string) {
    return apiRequest<PaymentListResponse>("/api/payments/me", { token });
  },
  getPayment(token: string, paymentId: number) {
    return apiRequest<PaymentResponse>(`/api/payments/${paymentId}`, { token });
  },
  requestRefund(token: string, paymentId: number, reason: string) {
    return apiRequest<PaymentResponse>(`/api/payments/${paymentId}/refund-request`, { method: "POST", token, body: { reason } });
  },

  // ── 환율 (공개 GET) ──
  getLatestExchangeRate(base = "USD", quote = "KRW") {
    return cachedPublicRequest(`fx:${base}:${quote}`, () =>
      apiRequest<ExchangeRateResponse>("/api/exchange-rates/latest", { query: { base, quote } })
    );
  },

  // ── 관리자: 플랫폼 수수료/정책 설정 ──
  getPlatformSettings(token: string) {
    return apiRequest<PlatformSettingsResponse>("/api/admin/settings/fees", { token });
  },
  updatePlatformSettings(token: string, body: UpdatePlatformSettingsRequest) {
    return apiRequest<PlatformSettingsResponse>("/api/admin/settings/fees", { method: "PUT", token, body });
  },

  // ── 관리자: 프로모 수수료 면제 (강사 10명) ──
  grantPromoWaiver(token: string, tutorProfileId: number) {
    return apiRequest<PromoWaiverResponse>(`/api/admin/tutors/${tutorProfileId}/promo-waiver`, {
      method: "POST",
      token
    });
  },
  revokePromoWaiver(token: string, tutorProfileId: number) {
    return apiRequest<PromoWaiverResponse>(`/api/admin/tutors/${tutorProfileId}/promo-waiver`, { method: "DELETE", token });
  },

  // ── 튜터 본인: 수익/인출/정산 ──
  getMyEarnings(token: string) {
    return apiRequest<TutorEarningsResponse>("/api/tutors/me/earnings", { token });
  },
  createWithdrawal(token: string, body: CreateWithdrawalRequest) {
    return apiRequest<WithdrawalResponse>("/api/tutors/me/withdrawals", { method: "POST", token, body });
  },
  getMyWithdrawals(token: string) {
    return apiRequest<WithdrawalListResponse>("/api/tutors/me/withdrawals", { token });
  },
  getMySettlements(token: string) {
    return apiRequest<SettlementListResponse>("/api/tutors/me/settlements", { token });
  },

  // ── 학생 본인: Haru Credits ──
  getMyCredits(token: string) {
    return apiRequest<HaruCreditResponse>("/api/credits/me", { token });
  },

  // ── 관리자: 인출 관리 ──
  getAdminWithdrawals(token: string, status?: WithdrawalStatus) {
    return apiRequest<WithdrawalListResponse>("/api/admin/withdrawals", { token, query: { status } });
  },
  approveWithdrawal(token: string, withdrawalId: number) {
    return apiRequest<WithdrawalResponse>(`/api/admin/withdrawals/${withdrawalId}/approve`, { method: "PATCH", token });
  },
  markWithdrawalPaid(token: string, withdrawalId: number) {
    return apiRequest<WithdrawalResponse>(`/api/admin/withdrawals/${withdrawalId}/paid`, { method: "PATCH", token });
  },
  rejectWithdrawal(token: string, withdrawalId: number, reason: string) {
    return apiRequest<WithdrawalResponse>(`/api/admin/withdrawals/${withdrawalId}/reject`, {
      method: "PATCH",
      token,
      body: { reason }
    });
  },

  // ── 관리자: 월정산 상태 전이 ──
  updateSettlementStatus(token: string, settlementId: number, status: SettlementStatus) {
    return apiRequest<MonthlySettlementResponse>(`/api/admin/settlements/${settlementId}/status`, {
      method: "PATCH",
      token,
      body: { status }
    });
  },

  // ── 관리자: 환불 요청 큐 / 환불 승인 -> Haru Credits 발급 ──
  getRefundRequests(token: string) {
    return apiRequest<RefundRequestResponse[]>("/api/admin/payments/refund-requests", { token });
  },
  approveRefund(token: string, paymentId: number) {
    return apiRequest<PaymentResponse>(`/api/admin/payments/${paymentId}/refund-approve`, { method: "POST", token });
  },

  // ── 채팅 ──
  startChat(token: string, tutorProfileId: number) {
    return apiRequest<ChatRoomSummary>("/api/chats", { method: "POST", token, body: { tutorProfileId } });
  },
  searchChatContacts(token: string, query: string) {
    return apiRequest<ContactListResponse>("/api/chats/contacts", { token, query: { query }, cache: "no-store" });
  },
  startChatWithUser(token: string, userId: number) {
    return apiRequest<ChatRoomSummary>("/api/chats/direct", { method: "POST", token, body: { userId } });
  },
  listChats(token: string) {
    return apiRequest<ChatRoomListResponse>("/api/chats", { token });
  },
  getChatMessages(token: string, chatRoomId: number, params?: { beforeId?: number; size?: number }) {
    return apiRequest<ChatMessageListResponse>(`/api/chats/${chatRoomId}/messages`, {
      token,
      query: { beforeId: params?.beforeId, size: params?.size }
    });
  },
  sendChatMessage(token: string, chatRoomId: number, body: string) {
    return apiRequest<ChatMessageResponse>(`/api/chats/${chatRoomId}/messages`, { method: "POST", token, body: { body } });
  },
  markChatRead(token: string, chatRoomId: number, lastMessageId: number) {
    return apiRequest<void>(`/api/chats/${chatRoomId}/read`, { method: "POST", token, body: { lastMessageId } });
  },
  uploadChatAttachment(token: string, chatRoomId: number, file: File) {
    const formData = new FormData();
    formData.append("file", file);
    return apiUpload<ChatMessageResponse>(`/api/chats/${chatRoomId}/attachments`, token, formData);
  },
  getChatUnreadCount(token: string) {
    return apiRequest<{ count: number }>("/api/chats/unread-count", { token });
  },

  // ── 개발자 도구 (dev tools) ──
  devStatus() {
    return apiRequest<{ enabled: boolean }>("/api/dev/status", { cache: "no-store" });
  },
  devCreateTutor(body: DevCreateTutorBody) {
    return apiRequest<DevCreateTutorResult>("/api/dev/tutors", { method: "POST", body });
  },
  devCreateStudent(body: { name?: string; email?: string; password?: string }) {
    return apiRequest<DevTestAccount>("/api/dev/students", { method: "POST", body });
  },
  devAddSlots(tutorProfileId: number, startAts: string[]) {
    return apiRequest<DevCreatedSlot[]>(`/api/dev/tutors/${tutorProfileId}/slots`, { method: "POST", body: { startAts } });
  },
  devCreateBooking(body: { tutorProfileId: number; startAt: string; studentUserId?: number }) {
    return apiRequest<DevCreatedLesson>("/api/dev/bookings", { method: "POST", body });
  }
};

/**
 * 카탈로그 학생 표시가 해석기. 백엔드 studentPrice25Amount(권장)를 쓰고,
 * 아직 없으면 lessonPrice25Amount로 폴백한다. (표시 전용 — 권위 가격은 checkout 응답.)
 */
export function resolveStudentPrice25(
  tutor: Pick<ExpertListResponse, "studentPrice25Amount" | "lessonPrice25Amount">
): number | string | null {
  const student = tutor.studentPrice25Amount;
  if (student !== null && student !== undefined && student !== "" && Number.isFinite(Number(student))) {
    return student;
  }
  return tutor.lessonPrice25Amount;
}

export function toMoney(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === "") return "$0.00";
  const amount = Number(value);
  if (!Number.isFinite(amount)) return "$0.00";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(amount);
}

export type FormatMoneyOptions = {
  /** 표시 통화. KRW면 fxRate로 환산해 표시. 기본 USD. */
  currency?: "USD" | "KRW";
  /** USD->KRW 환율. KRW 표시인데 없으면 USD로 폴백. */
  fxRate?: number | null;
};

/**
 * 진실 원장 USD 값을 표시 통화로 포맷한다.
 * - currency 'USD' (기본): "$1,234.56"
 * - currency 'KRW' + fxRate: 환산 후 원화(소수 없음). fxRate 누락 시 USD로 폴백.
 * - 사이버머니/Haru Credits/정산 잔액은 항상 USD로 호출(통화토글 무시) — 호출부에서 currency 미지정.
 */
export function formatMoney(usdValue: number | string | null | undefined, options: FormatMoneyOptions = {}) {
  const { currency = "USD", fxRate } = options;
  const usdAmount = usdValue === null || usdValue === undefined || usdValue === "" ? 0 : Number(usdValue);
  const safeUsd = Number.isFinite(usdAmount) ? usdAmount : 0;

  if (currency === "KRW" && typeof fxRate === "number" && Number.isFinite(fxRate) && fxRate > 0) {
    return new Intl.NumberFormat("ko-KR", {
      style: "currency",
      currency: "KRW",
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(safeUsd * fxRate);
  }

  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(safeUsd);
}

/** 요율(0.10)을 퍼센트 라벨("10%")로. */
export function formatRatePercent(rate: number | string | null | undefined, fractionDigits = 0) {
  if (rate === null || rate === undefined || rate === "") return "-";
  const value = Number(rate);
  if (!Number.isFinite(value)) return "-";
  return `${(value * 100).toFixed(fractionDigits)}%`;
}

export function dateRangeFromToday(days = 30) {
  const from = new Date();
  from.setHours(0, 0, 0, 0);
  const to = new Date(from);
  to.setDate(to.getDate() + days);
  return { from: from.toISOString(), to: to.toISOString() };
}
