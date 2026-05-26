"use client";

import { ArrowRight, BookOpenCheck, Clock, Home, Search, Video } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { BookingParticipant, BookingResponse, haruApi } from "../api";
import { useAuth } from "../auth";
import { ApiNotice, AppHeader, Badge, Button, EmptyState, SectionHeader, StatCard, TutorPortrait } from "../components";

function formatDateTime(value: string) {
  const date = new Date(value);
  return {
    date: date.toLocaleDateString("ko-KR", { month: "long", day: "numeric", weekday: "short" }),
    time: date.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })
  };
}

function statusLabel(status: BookingResponse["status"]) {
  if (status === "CANCELLED") return "취소";
  if (status === "COMPLETED") return "완료";
  return "예약";
}

function canCancel(booking: BookingResponse) {
  return booking.status === "RESERVED" && new Date().getTime() < new Date(booking.startAt).getTime() - 3 * 60 * 60 * 1000;
}

function lessonDisplayName(booking: BookingResponse, participant: BookingParticipant) {
  if (participant === "tutor") {
    return booking.studentName?.trim() || `학생 #${booking.studentUserId}`;
  }
  return booking.tutorDisplayName?.trim() || `튜터 #${booking.tutorProfileId}`;
}

function lessonSubtitle(booking: BookingResponse, participant: BookingParticipant) {
  if (participant === "tutor") {
    return "학생이 예약한 1:1 한국어 수업입니다.";
  }
  return booking.tutorShortIntroduction?.trim() || "1:1 한국어 수업이 준비되어 있습니다.";
}

export default function BookingsPage() {
  const { accessToken, loading: authLoading, user } = useAuth();
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [reviewBookingId, setReviewBookingId] = useState<number | null>(null);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewBody, setReviewBody] = useState("");
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const participant: BookingParticipant = user?.activeRole === "TUTOR" ? "tutor" : "student";
  const isTutorMode = participant === "tutor";

  function loadBookings() {
    if (!accessToken) return;
    setLoading(true);
    setError(null);
    haruApi
      .getMyBookings(accessToken, participant)
      .then((response) => setBookings(response.bookings))
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(loadBookings, [accessToken, participant]);

  const upcoming = useMemo(
    () => bookings.filter((booking) => booking.status === "RESERVED").sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime()),
    [bookings]
  );
  const completed = useMemo(() => bookings.filter((booking) => booking.status === "COMPLETED"), [bookings]);
  const cancelled = useMemo(() => bookings.filter((booking) => booking.status === "CANCELLED"), [bookings]);
  const nextBooking = upcoming[0] ?? null;

  async function cancelBooking(id: number) {
    if (!accessToken) return;
    setError(null);
    try {
      await haruApi.cancelBooking(accessToken, id, "학생이 직접 취소 요청");
      loadBookings();
    } catch (err) {
      setError(err instanceof Error ? err.message : "예약 취소에 실패했습니다.");
    }
  }

  async function submitReview() {
    if (!accessToken || !reviewBookingId) return;
    setError(null);
    setMessage(null);
    setReviewSubmitting(true);
    try {
      await haruApi.createReview(accessToken, reviewBookingId, { rating: reviewRating, body: reviewBody });
      setMessage("후기가 등록되었습니다. 튜터 공개 프로필에 반영됩니다.");
      setReviewBookingId(null);
      setReviewRating(5);
      setReviewBody("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "후기 등록에 실패했습니다.");
    } finally {
      setReviewSubmitting(false);
    }
  }

  return (
    <main className="bookings-layout page-shell compact refined-bookings">
      <div className="bookings-header-wrap">
        <AppHeader />
      </div>
      <aside className="summary-rail">
        <SectionHeader eyebrow={isTutorMode ? "Tutor Lessons" : "My Lessons"} title={isTutorMode ? "내 수업" : "내 예약"} description={isTutorMode ? "학생이 예약한 수업과 수업방 입장 가능 여부를 한곳에서 확인합니다." : "예약한 수업, 입장 시간, 취소 가능 여부를 한곳에서 확인합니다."} />
        <StatCard label="예정 수업" value={String(upcoming.length)} hint="Jitsi 수업방 연결" />
        <StatCard label="완료 수업" value={String(completed.length)} hint={isTutorMode ? "지난 담당 수업" : "복습과 후기 작성"} />
        <StatCard label="취소된 수업" value={String(cancelled.length)} hint="변경 내역 확인" />
        <Button as="a" href={isTutorMode ? "/tutor/dashboard" : "/"} variant="secondary" className="wide">
          <Home size={16} /> {isTutorMode ? "튜터 센터" : "홈으로"}
        </Button>
        <Button as="a" href={isTutorMode ? "/tutor/schedule" : "/tutors"} variant="secondary" className="wide">
          <Search size={16} /> {isTutorMode ? "일정 관리" : "전체 튜터 보기"}
        </Button>
      </aside>

      <section className="bookings-column">
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {!authLoading && !accessToken ? (
          <section className="panel booking-empty-panel">
            <EmptyState title="로그인이 필요합니다" body={isTutorMode ? "내 수업과 Jitsi 수업방을 확인하려면 먼저 로그인해 주세요." : "내 예약과 Jitsi 수업방을 확인하려면 먼저 로그인해 주세요."} />
            <Button as="a" href="/login">
              로그인하기
            </Button>
          </section>
        ) : null}

        {accessToken && nextBooking ? <NextLessonCard booking={nextBooking} participant={participant} /> : null}

        <section className="panel booking-section">
          <SectionHeader eyebrow="Upcoming" title={isTutorMode ? "예정된 담당 수업" : "예정된 수업"} description="수업 시작 10분 전부터 Jitsi 수업방 입장이 가능합니다." />
          {loading ? <EmptyState title="예약을 불러오는 중" body="수업 일정을 확인하고 있습니다." /> : null}
          {!authLoading && !loading && upcoming.length === 0 ? (
            <div className="booking-empty-panel">
              <EmptyState
                title={accessToken ? (isTutorMode ? "예정된 담당 수업이 없습니다" : "예정된 수업이 없습니다") : (isTutorMode ? "담당 수업이 없습니다" : "예약한 수업이 없습니다")}
                body={accessToken ? (isTutorMode ? "공개 일정을 추가하면 학생 예약이 이곳에 표시됩니다." : "튜터 프로필에서 가능한 시간을 선택해 첫 수업을 예약해 보세요.") : (isTutorMode ? "로그인 후 담당 수업이 생기면 이곳에 표시됩니다." : "로그인 후 예약하면 수업 일정과 입장 버튼이 여기에 표시됩니다.")}
              />
              <Button as="a" href={isTutorMode ? "/tutor/schedule" : "/tutors"}>
                {isTutorMode ? "공개 시간 추가" : "튜터 찾기"}
              </Button>
            </div>
          ) : null}
          <div className="booking-card-list">
            {upcoming.map((booking) => (
              <BookingCard booking={booking} key={booking.id} participant={participant} onCancel={!isTutorMode && canCancel(booking) ? () => void cancelBooking(booking.id) : undefined} />
            ))}
          </div>
        </section>

        <section className="panel prep-card booking-prep-card">
          <div className="prep-icon">
            <BookOpenCheck size={24} />
          </div>
          <div>
            <Badge tone="orange">수업 준비</Badge>
            <h2>{isTutorMode ? "수업 시작 전 10분 체크" : "입장 전 10분 체크"}</h2>
            <p>{isTutorMode ? "마이크, 카메라, 자료 공유를 미리 확인하고 학생이 입장할 때 바로 수업을 시작할 수 있게 준비해 주세요." : "마이크와 카메라 권한을 확인하고, 튜터에게 보낼 질문이나 자료를 미리 준비해 주세요."}</p>
          </div>
        </section>

        <section className="panel booking-section">
          <SectionHeader eyebrow="History" title={isTutorMode ? "지난 담당 수업" : "완료/취소된 수업"} />
          <div className="booking-card-list">
            {[...completed, ...cancelled].map((booking) => (
              <div key={booking.id}>
                <BookingCard
                  booking={booking}
                  participant={participant}
                  onReview={!isTutorMode && booking.status === "COMPLETED" ? () => {
                    setReviewBookingId(booking.id);
                    setReviewRating(5);
                    setReviewBody("");
                  } : undefined}
                />
                {reviewBookingId === booking.id ? (
                  <div className="review-compose">
                    <label>
                      별점
                      <select value={reviewRating} onChange={(event) => setReviewRating(Number(event.target.value))}>
                        <option value={5}>5</option>
                        <option value={4}>4</option>
                        <option value={3}>3</option>
                        <option value={2}>2</option>
                        <option value={1}>1</option>
                      </select>
                    </label>
                    <label>
                      후기
                      <textarea value={reviewBody} onChange={(event) => setReviewBody(event.target.value)} placeholder="수업에서 좋았던 점을 적어주세요." />
                    </label>
                    <div className="button-row">
                      <Button onClick={() => void submitReview()} disabled={reviewSubmitting || !reviewBody.trim()}>
                        후기 등록
                      </Button>
                      <Button variant="secondary" onClick={() => setReviewBookingId(null)}>
                        취소
                      </Button>
                    </div>
                  </div>
                ) : null}
              </div>
            ))}
          </div>
          {!authLoading && completed.length + cancelled.length === 0 ? <EmptyState title={isTutorMode ? "지난 담당 수업이 없습니다" : "지난 수업이 없습니다"} body={isTutorMode ? "완료되거나 취소된 담당 수업이 여기에 표시됩니다." : "완료되거나 취소된 수업이 여기에 표시됩니다."} /> : null}
        </section>
      </section>
    </main>
  );
}

function NextLessonCard({ booking, participant }: { booking: BookingResponse; participant: BookingParticipant }) {
  const { date, time } = formatDateTime(booking.startAt);
  const isTutorMode = participant === "tutor";
  return (
    <section className="panel next-lesson-card">
      <div className="next-lesson-copy">
        <Badge tone={booking.joinAvailable ? "green" : "orange"}>{booking.joinAvailable ? "입장 가능" : "입장 대기"}</Badge>
        <h2>{isTutorMode ? "다음 담당 수업" : "다음 수업"}</h2>
        <p>
          {lessonDisplayName(booking, participant)} · {date} {time} · {booking.lessonDurationMinutes}분
        </p>
        <span>{lessonSubtitle(booking, participant)}</span>
      </div>
      <div className="next-lesson-actions">
        {booking.joinAvailable ? (
          <Button as="a" href={`/bookings/${booking.id}/classroom`}>
            <Video size={16} /> 수업방 열기
          </Button>
        ) : (
          <Button disabled>
            <Video size={16} /> 수업 시작 10분 전부터 입장 가능
          </Button>
        )}
        <Button as="a" href={isTutorMode ? "/tutor/schedule" : "/tutors"} variant="secondary">
          {isTutorMode ? "일정 관리" : "다른 수업 예약"}
        </Button>
      </div>
    </section>
  );
}

function BookingCard({ booking, participant, onCancel, onReview }: { booking: BookingResponse; participant: BookingParticipant; onCancel?: () => void; onReview?: () => void }) {
  const { date, time } = formatDateTime(booking.startAt);
  const isReserved = booking.status === "RESERVED";
  const reservedBadgeTone = booking.joinAvailable ? "green" : "orange";
  const reservedBadgeLabel = booking.joinAvailable ? "입장 가능" : "입장 대기";
  return (
    <article className="lesson-booking-card">
      <div className="lesson-booking-icon">
        <TutorPortrait imageUrl={booking.tutorImageUrl} label={lessonDisplayName(booking, participant).slice(0, 1)} />
      </div>
      <div className="lesson-booking-main">
        <div className="lesson-booking-title-row">
          <strong>{lessonDisplayName(booking, participant)}</strong>
          <Badge tone={booking.status === "COMPLETED" ? "blue" : booking.status === "CANCELLED" ? "red" : reservedBadgeTone}>{isReserved ? reservedBadgeLabel : statusLabel(booking.status)}</Badge>
        </div>
        <p>{lessonSubtitle(booking, participant)}</p>
        <p>
          {date} {time} · {booking.lessonDurationMinutes}분
        </p>
        <span>
          <Clock size={14} /> {isReserved ? "수업 시작 10분 전부터 입장할 수 있습니다." : booking.cancelReason ?? (participant === "tutor" ? "담당 수업 기록" : "수업 기록")}
        </span>
      </div>
      <div className="lesson-booking-actions">
        {isReserved && booking.joinAvailable ? (
          <Button as="a" href={`/bookings/${booking.id}/classroom`}>
            수업방 열기 <ArrowRight size={16} />
          </Button>
        ) : null}
        {isReserved && !booking.joinAvailable ? (
          <Button disabled>
            수업 시작 10분 전부터 입장 가능
          </Button>
        ) : null}
        {onCancel ? (
          <Button variant="secondary" onClick={onCancel}>
            취소하기
          </Button>
        ) : null}
        {onReview ? (
          <Button variant="secondary" onClick={onReview}>
            후기 작성
          </Button>
        ) : null}
      </div>
    </article>
  );
}
