"use client";

import { BookOpenCheck, CalendarClock, Video } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { BookingResponse, haruApi } from "../api";
import { useAuth } from "../auth";
import { ApiNotice, Badge, BookingItem, Button, CalendarMock, EmptyState, SectionHeader, StatCard } from "../components";
import { bookings as mockBookings } from "../data";

function bookingToView(booking: BookingResponse) {
  return {
    tutor: `튜터 #${booking.tutorProfileId}`,
    date: new Date(booking.startAt).toLocaleDateString("ko-KR"),
    time: new Date(booking.startAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" }),
    duration: `${booking.lessonDurationMinutes}분`,
    status: booking.status === "CANCELLED" ? ("취소" as const) : booking.status === "COMPLETED" ? ("완료" as const) : ("예약" as const),
    avatar: "T"
  };
}

export default function BookingsPage() {
  const { accessToken } = useAuth();
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [joinMessage, setJoinMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function loadBookings() {
    if (!accessToken) return;
    haruApi
      .getMyBookings(accessToken)
      .then((response) => setBookings(response.bookings))
      .catch((err: Error) => setError(err.message));
  }

  useEffect(loadBookings, [accessToken]);

  const upcoming = useMemo(() => bookings.filter((booking) => booking.status === "RESERVED"), [bookings]);
  const completed = useMemo(() => bookings.filter((booking) => booking.status === "COMPLETED"), [bookings]);
  const cancelled = useMemo(() => bookings.filter((booking) => booking.status === "CANCELLED"), [bookings]);

  async function cancelBooking(id: number) {
    if (!accessToken) return;
    setError(null);
    setJoinMessage(null);
    try {
      await haruApi.cancelBooking(accessToken, id, "학생이 직접 취소 요청");
      loadBookings();
    } catch (err) {
      setError(err instanceof Error ? err.message : "예약 취소에 실패했습니다.");
    }
  }

  async function joinBooking(id: number) {
    if (!accessToken) return;
    setError(null);
    setJoinMessage(null);
    try {
      const response = await haruApi.joinBooking(accessToken, id);
      setJoinMessage(response.joinAvailable ? `입장 가능: ${response.joinUrl ?? response.message}` : response.message);
    } catch (err) {
      setError(err instanceof Error ? err.message : "입장 가능 여부 조회에 실패했습니다.");
    }
  }

  return (
    <main className="bookings-layout page-shell compact">
      <aside className="summary-rail">
        <SectionHeader eyebrow="My Lessons" title="내 예약" description="예정, 완료, 취소 수업을 한 곳에서 확인합니다." />
        <StatCard label="예정된 수업" value={String(upcoming.length)} hint="Jitsi 입장 가능 여부 표시" />
        <StatCard label="완료 수업" value={String(completed.length)} hint="복습과 후기 작성 대상" />
        <StatCard label="취소/변경" value={String(cancelled.length)} hint="변경 가능한 수업 구분" />
      </aside>

      <CalendarMock />

      <section className="bookings-column">
        <section className="panel booking-section">
          <SectionHeader eyebrow="Upcoming" title="예정된 수업" />
          {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
          {joinMessage ? <ApiNotice type="info">{joinMessage}</ApiNotice> : null}
          {!accessToken ? <EmptyState title="로그인이 필요합니다" body="내 예약을 보려면 먼저 로그인해주세요." /> : null}
          {accessToken && upcoming.length === 0 ? (
            <EmptyState title="예정된 수업이 없습니다" body="튜터 프로필에서 공개 일정을 선택해 첫 수업을 예약하세요." />
          ) : null}
          <div className="booking-list">
            {upcoming.map((booking) => (
              <article className="booking-item actionable-booking" key={booking.id}>
                <BookingItem booking={bookingToView(booking)} />
                <div className="button-row">
                  <Button variant="primary" onClick={() => void joinBooking(booking.id)}>
                    <Video size={16} /> Jitsi Meet 입장
                  </Button>
                  <Button variant="secondary" onClick={() => void cancelBooking(booking.id)}>
                    취소
                  </Button>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className="panel prep-card">
          <div className="prep-icon">
            <BookOpenCheck size={24} />
          </div>
          <div>
            <Badge tone="orange">다음 수업 준비</Badge>
            <h2>수업 전 10분 체크</h2>
            <p>마이크와 카메라를 확인하고, 튜터가 보낸 자료와 질문을 채팅에서 미리 열어두세요.</p>
          </div>
        </section>

        <section className="panel booking-section">
          <SectionHeader eyebrow="History" title="완료/취소된 수업" />
          <div className="booking-list">
            {[...completed, ...cancelled].map((booking) => (
              <BookingItem booking={bookingToView(booking)} key={booking.id} />
            ))}
            {completed.length + cancelled.length === 0
              ? mockBookings.slice(2).map((booking) => <BookingItem booking={booking} key={`${booking.tutor}-${booking.status}`} />)
              : null}
          </div>
        </section>

        <section className="panel available-box">
          <CalendarClock size={22} />
          <div>
            <h2>새 수업을 찾고 있나요?</h2>
            <p>오늘 가능한 튜터를 둘러보고 다음 수업을 예약하세요.</p>
          </div>
          <Button as="a" href="/" className="wide">
            튜터 찾기
          </Button>
        </section>
      </section>
    </main>
  );
}
