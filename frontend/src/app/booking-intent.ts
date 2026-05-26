export type PendingBookingIntent = {
  tutorProfileId: number;
  scheduleSlotId: number;
  lessonDurationMinutes: number;
  paymentId: number;
  createdAt: string;
};

const STORAGE_KEY = "haru.pendingBookingIntent";
const MAX_AGE_MS = 6 * 60 * 60 * 1000;

function canUseSessionStorage() {
  return typeof window !== "undefined" && typeof window.sessionStorage !== "undefined";
}

function isValidIntent(value: unknown): value is PendingBookingIntent {
  if (!value || typeof value !== "object") return false;

  const intent = value as Record<string, unknown>;
  return Number.isFinite(intent.tutorProfileId)
    && Number.isFinite(intent.scheduleSlotId)
    && Number.isFinite(intent.lessonDurationMinutes)
    && Number.isFinite(intent.paymentId)
    && typeof intent.createdAt === "string";
}

export function readPendingBookingIntent() {
  if (!canUseSessionStorage()) return null;

  const raw = window.sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return null;

  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!isValidIntent(parsed)) {
      window.sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }

    if (Date.now() - new Date(parsed.createdAt).getTime() > MAX_AGE_MS) {
      window.sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }

    return parsed;
  } catch {
    window.sessionStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function writePendingBookingIntent(intent: PendingBookingIntent) {
  if (!canUseSessionStorage()) return;
  window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(intent));
}

export function clearPendingBookingIntent() {
  if (!canUseSessionStorage()) return;
  window.sessionStorage.removeItem(STORAGE_KEY);
}