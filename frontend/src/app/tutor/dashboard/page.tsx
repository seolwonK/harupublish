"use client";

import { CalendarCheck, CheckCircle2, Clock, DollarSign, FileCheck2, Video } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { BookingResponse, dateRangeFromToday, haruApi, ScheduleSlotResponse, TutorProfileResponse } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, Avatar, Badge, Button, EmptyState, SectionHeader, Sidebar, StatCard, statusLabel } from "../../components";

function profileCompletion(profile: TutorProfileResponse | null, slotCount: number) {
  return [profile?.displayName, profile?.shortIntroduction, profile?.aboutMe, profile?.whatIOffer, profile?.availableLanguages?.length, slotCount > 0]
    .filter(Boolean).length;
}

export default function TutorDashboardPage() {
  const { accessToken, user } = useAuth();
  const [profile, setProfile] = useState<TutorProfileResponse | null>(null);
  const [slots, setSlots] = useState<ScheduleSlotResponse[]>([]);
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    const range = dateRangeFromToday(30);
    Promise.all([
      haruApi.getMyTutorProfile(accessToken).catch(() => null),
      haruApi.getMySchedule(accessToken, range.from, range.to).catch(() => ({ slots: [] })),
      haruApi.getMyBookings(accessToken, "tutor").catch(() => ({ bookings: [] }))
    ])
      .then(([nextProfile, schedule, bookingResponse]) => {
        setProfile(nextProfile);
        setSlots(schedule.slots);
        setBookings(bookingResponse.bookings);
      })
      .catch((err: Error) => setError(err.message));
  }, [accessToken]);

  const approved = (profile?.status ?? user?.tutorProfileStatus) === "APPROVED";
  const completion = profileCompletion(profile, slots.length);
  const upcomingBookings = useMemo(
    () => bookings.filter((booking) => booking.status === "RESERVED").sort((left, right) => new Date(left.startAt).getTime() - new Date(right.startAt).getTime()),
    [bookings]
  );
  const completedBookings = useMemo(() => bookings.filter((booking) => booking.status === "COMPLETED"), [bookings]);
  const todayBookingCount = useMemo(() => {
    const today = new Date().toDateString();
    return bookings.filter((booking) => new Date(booking.startAt).toDateString() === today).length;
  }, [bookings]);
  const nextBooking = upcomingBookings[0] ?? null;
  const nextActionTitle = approved ? "이번 주 가능한 수업 시간을 추가하세요" : "프로필을 작성하고 승인 요청을 보내세요";
  const nextActionBody = approved
    ? "학생은 공개된 시간에서만 예약할 수 있습니다. 비어 있는 시간을 먼저 채우면 예약 전환율이 올라갑니다."
    : "이름, 소개, 언어, 가격, 수업 내용을 채운 뒤 관리자의 승인을 요청할 수 있습니다. 이미지는 선택 사항입니다.";

  return (
    <main className="dashboard-layout">
      <Sidebar />
      <section className="dashboard-main">
        <header className="dashboard-head">
          <div>
            <Badge tone={approved ? "green" : "orange"}>{statusLabel(profile?.status ?? user?.tutorProfileStatus)}</Badge>
            <h1>튜터 센터</h1>
            <p>공개 프로필, 수업 가능 시간, 예약 준비 상태를 한곳에서 확인합니다.</p>
          </div>
          <div className="dashboard-user">
            <Avatar label={user?.name?.slice(0, 1) ?? "A"} />
            <strong>{user?.name ?? "튜터"}</strong>
          </div>
        </header>
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        {!accessToken ? <EmptyState title="로그인이 필요합니다" body="튜터 센터는 로그인 후 사용할 수 있습니다." /> : null}

        <div className="stats-grid">
          <StatCard label="오늘 수업" value={String(todayBookingCount)} hint="담당 수업 기준" />
          <StatCard label="공개 시간" value={String(slots.length)} hint="30일 내 등록된 슬롯" />
          <StatCard label="완료 수업" value={String(completedBookings.length)} hint="완료된 담당 수업" />
          <StatCard label="예상 수익" value="USD 0.00" hint="정산 API 연동 예정" />
        </div>

        {approved ? (
          <section className="panel next-action-card tutor-live-card">
            <div className="prep-icon">
              <Video size={24} />
            </div>
            <div>
              <Badge tone={nextBooking?.joinAvailable ? "green" : "orange"}>{nextBooking?.joinAvailable ? "입장 가능" : "다음 수업"}</Badge>
              <h2>{nextBooking ? (nextBooking.studentName?.trim() || `학생 #${nextBooking.studentUserId}`) : "예정된 담당 수업이 없습니다"}</h2>
              <p>
                {nextBooking
                  ? `${new Date(nextBooking.startAt).toLocaleDateString("ko-KR", { month: "long", day: "numeric", weekday: "short" })} ${new Date(nextBooking.startAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })} · ${nextBooking.lessonDurationMinutes}분`
                  : "학생이 예약하면 이곳에서 바로 수업방 입장 또는 내 수업 이동이 가능합니다."}
              </p>
              {nextBooking ? <span className="tutor-live-meta"><Clock size={14} /> {nextBooking.joinAvailable ? "지금 바로 수업방에 입장할 수 있습니다." : "수업 시작 10분 전부터 수업방 입장이 열립니다."}</span> : null}
            </div>
            {nextBooking ? (
              nextBooking.joinAvailable ? (
                <Button as="a" href={`/bookings/${nextBooking.id}/classroom`}>
                  <Video size={16} /> 수업방 열기
                </Button>
              ) : (
                <Button as="a" href="/bookings" variant="secondary">
                  내 수업 보기
                </Button>
              )
            ) : (
              <Button as="a" href="/tutor/schedule">
                일정 관리
              </Button>
            )}
          </section>
        ) : null}

        <section className="panel next-action-card">
          <div className="prep-icon">
            <FileCheck2 size={24} />
          </div>
          <div>
            <Badge tone="brand">다음 단계</Badge>
            <h2>{nextActionTitle}</h2>
            <p>{nextActionBody}</p>
          </div>
          <Button as="a" href={approved ? "/tutor/schedule" : "/tutor/profile"}>
            {approved ? "일정 관리" : "프로필 완성"}
          </Button>
        </section>

        <div className="dashboard-grid">
          <section className="panel dashboard-panel">
            <SectionHeader eyebrow="Schedule" title="다가오는 공개 시간" action={<Button as="a" href="/tutor/schedule" variant="secondary">관리</Button>} />
            {slots.length === 0 ? <EmptyState title="등록된 시간이 없습니다" body="일정 관리에서 수업 가능한 시간을 추가하세요." /> : null}
            {slots.slice(0, 5).map((slot) => (
              <article className="schedule-row" key={slot.id}>
                <CalendarCheck size={18} />
                <strong>{new Date(slot.startAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })}</strong>
                <span>{new Date(slot.startAt).toLocaleDateString("ko-KR")}</span>
                <Badge tone="green">공개</Badge>
              </article>
            ))}
          </section>

          <section className="panel dashboard-panel">
            <SectionHeader eyebrow="Profile" title="등록 준비도" description={`${completion}/6 항목 완료`} />
            <div className="progress-track">
              <span style={{ width: `${(completion / 6) * 100}%` }} />
            </div>
            <div className="info-list">
              <p><strong>표시 이름</strong><span>{profile?.displayName ?? "-"}</span></p>
              <p><strong>짧은 소개</strong><span>{profile?.shortIntroduction ?? "-"}</span></p>
              <p><strong>언어</strong><span>{profile?.availableLanguages?.join(", ") || "-"}</span></p>
            </div>
            <Button as="a" href="/tutor/profile" className="wide">프로필 관리</Button>
          </section>

          <section className="panel mini-dashboard-card">
            <CheckCircle2 size={22} />
            <strong>완료 수업</strong>
            <span>수업이 완료되면 후기 작성, 정산 가능 상태와 함께 표시됩니다.</span>
          </section>
          <section className="panel mini-dashboard-card">
            <DollarSign size={22} />
            <strong>수익 관리</strong>
            <span>결제와 정산 데이터가 연결되면 예상 수익과 지급 상태를 확인할 수 있습니다.</span>
          </section>
        </div>
      </section>
    </main>
  );
}
