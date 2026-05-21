"use client";

import { ArrowRight, BookOpenCheck, CalendarClock, Clock, Home, Search, Video, XCircle } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { BookingResponse, haruApi } from "../api";
import { useAuth } from "../auth";
import { ApiNotice, AppHeader, Badge, Button, EmptyState, SectionHeader, StatCard } from "../components";

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

export default function BookingsPage() {
  const { accessToken, loading: authLoading } = useAuth();
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function loadBookings() {
    if (!accessToken) return;
    setLoading(true);
    setError(null);
    haruApi
      .getMyBookings(accessToken, "student")
      .then((response) => setBookings(response.bookings))
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(loadBookings, [accessToken]);

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

  return (
    <main className="bookings-layout page-shell compact refined-bookings">
      <div className="bookings-header-wrap">
        <AppHeader />
      </div>
      <aside className="summary-rail">
        <SectionHeader eyebrow="My Lessons" title="내 예약" description="예약한 수업, 입장 시간, 취소 가능 여부를 한곳에서 확인합니다." />
        <StatCard label="예정 수업" value={String(upcoming.length)} hint="Jitsi 수업방 연결" />
        <StatCard label="완료 수업" value={String(completed.length)} hint="복습과 후기 작성" />
        <StatCard label="취소된 수업" value={String(cancelled.length)} hint="변경 내역 확인" />
        <Button as="a" href="/" variant="secondary" className="wide">
          <Home size={16} /> 홈으로
        </Button>
        <Button as="a" href="/tutors" variant="secondary" className="wide">
          <Search size={16} /> 전체 튜터 보기
        </Button>
      </aside>

      <section className="bookings-column">
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        {!authLoading && !accessToken ? (
          <section className="panel booking-empty-panel">
            <EmptyState title="로그인이 필요합니다" body="내 예약과 Jitsi 수업방을 확인하려면 먼저 로그인해 주세요." />
            <Button as="a" href="/login">
              로그인하기
            </Button>
          </section>
        ) : null}

        {accessToken && nextBooking ? <NextLessonCard booking={nextBooking} /> : null}

        <section className="panel booking-section">
          <SectionHeader eyebrow="Upcoming" title="예정된 수업" description="수업 시작 10분 전부터 Jitsi 수업방 입장이 가능합니다." />
          {loading ? <EmptyState title="예약을 불러오는 중" body="수업 일정을 확인하고 있습니다." /> : null}
          {!authLoading && !loading && upcoming.length === 0 ? (
            <div className="booking-empty-panel">
              <EmptyState
                title={accessToken ? "예정된 수업이 없습니다" : "예약한 수업이 없습니다"}
                body={accessToken ? "튜터 프로필에서 가능한 시간을 선택해 첫 수업을 예약해 보세요." : "로그인 후 예약하면 수업 일정과 입장 버튼이 여기에 표시됩니다."}
              />
              <Button as="a" href="/tutors">
                튜터 찾기
              </Button>
            </div>
          ) : null}
          <div className="booking-card-list">
            {upcoming.map((booking) => (
              <BookingCard booking={booking} key={booking.id} onCancel={canCancel(booking) ? () => void cancelBooking(booking.id) : undefined} />
            ))}
          </div>
        </section>

        <section className="panel prep-card booking-prep-card">
          <div className="prep-icon">
            <BookOpenCheck size={24} />
          </div>
          <div>
            <Badge tone="orange">수업 준비</Badge>
            <h2>입장 전 10분 체크</h2>
            <p>마이크와 카메라 권한을 확인하고, 튜터에게 보낼 질문이나 자료를 미리 준비해 주세요.</p>
          </div>
        </section>

        <section className="panel booking-section">
          <SectionHeader eyebrow="History" title="완료/취소된 수업" />
          <div className="booking-card-list">
            {[...completed, ...cancelled].map((booking) => (
              <BookingCard booking={booking} key={booking.id} />
            ))}
          </div>
          {!authLoading && completed.length + cancelled.length === 0 ? <EmptyState title="지난 수업이 없습니다" body="완료되거나 취소된 수업이 여기에 표시됩니다." /> : null}
        </section>
      </section>
    </main>
  );
}

function NextLessonCard({ booking }: { booking: BookingResponse }) {
  const { date, time } = formatDateTime(booking.startAt);
  return (
    <section className="panel next-lesson-card">
      <div className="next-lesson-copy">
        <Badge tone={booking.joinAvailable ? "green" : "orange"}>{booking.joinAvailable ? "입장 가능" : "예약 완료"}</Badge>
        <h2>다음 수업</h2>
        <p>
          튜터 #{booking.tutorProfileId} · {date} {time} · {booking.lessonDurationMinutes}분
        </p>
      </div>
      <div className="next-lesson-actions">
        <Button as="a" href={`/bookings/${booking.id}/classroom`}>
          <Video size={16} /> 수업방 열기
        </Button>
        <Button as="a" href="/tutors" variant="secondary">
          다른 수업 예약
        </Button>
      </div>
    </section>
  );
}

function BookingCard({ booking, onCancel }: { booking: BookingResponse; onCancel?: () => void }) {
  const { date, time } = formatDateTime(booking.startAt);
  const isReserved = booking.status === "RESERVED";
  return (
    <article className="lesson-booking-card">
      <div className="lesson-booking-icon">
        {isReserved ? <Video size={20} /> : booking.status === "CANCELLED" ? <XCircle size={20} /> : <CalendarClock size={20} />}
      </div>
      <div className="lesson-booking-main">
        <div className="lesson-booking-title-row">
          <strong>튜터 #{booking.tutorProfileId}</strong>
          <Badge tone={booking.status === "COMPLETED" ? "blue" : booking.status === "CANCELLED" ? "red" : "green"}>{statusLabel(booking.status)}</Badge>
        </div>
        <p>
          {date} {time} · {booking.lessonDurationMinutes}분
        </p>
        <span>
          <Clock size={14} /> {isReserved ? "수업 시작 10분 전부터 Jitsi 수업방이 열립니다." : booking.cancelReason ?? "수업 기록"}
        </span>
      </div>
      <div className="lesson-booking-actions">
        {isReserved ? (
          <Button as="a" href={`/bookings/${booking.id}/classroom`}>
            수업방 열기 <ArrowRight size={16} />
          </Button>
        ) : null}
        {onCancel ? (
          <Button variant="secondary" onClick={onCancel}>
            취소하기
          </Button>
        ) : null}
      </div>
    </article>
  );
}
