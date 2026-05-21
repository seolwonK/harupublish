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
};

export type TutorScheduleResponse = {
  slots: ScheduleSlotResponse[];
};

export type BookingResponse = {
  id: number;
  studentUserId: number;
  tutorProfileId: number;
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

export type SessionTokens = {
  accessToken: string;
  refreshToken: string;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const API_ORIGIN = new URL(API_BASE_URL).origin;

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
};

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

  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? "GET",
      headers: {
        ...(options.body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {})
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });
  } catch {
    throw new HaruApiError(
      "백엔드 서버에 연결할 수 없습니다. Spring Boot 서버가 http://localhost:8080 에서 실행 중인지 확인해주세요.",
      "NETWORK_ERROR",
      0
    );
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
    return apiRequest<ExpertListResponse[]>("/api/tutors");
  },
  getTutor(id: number) {
    return apiRequest<TutorProfileResponse>(`/api/tutors/${id}`);
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
    return apiRequest<TutorScheduleResponse>(`/api/tutors/${tutorProfileId}/schedule`, { query: { from, to } });
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
  getMyBookings(token: string) {
    return apiRequest<BookingListResponse>("/api/bookings/me", { token });
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
  }
};

export function toMoney(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === "") return "USD 0";
  return `USD ${Number(value).toFixed(2)}`;
}

export function dateRangeFromToday(days = 30) {
  const from = new Date();
  from.setHours(0, 0, 0, 0);
  const to = new Date(from);
  to.setDate(to.getDate() + days);
  return { from: from.toISOString(), to: to.toISOString() };
}
