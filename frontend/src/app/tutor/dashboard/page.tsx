"use client";

import { CalendarCheck, CheckCircle2, DollarSign, FileCheck2 } from "lucide-react";
import { useEffect, useState } from "react";
import { dateRangeFromToday, haruApi, ScheduleSlotResponse, TutorProfileResponse } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, Avatar, Badge, Button, EmptyState, SectionHeader, Sidebar, StatCard, statusLabel } from "../../components";

export default function TutorDashboardPage() {
  const { accessToken, user } = useAuth();
  const [profile, setProfile] = useState<TutorProfileResponse | null>(null);
  const [slots, setSlots] = useState<ScheduleSlotResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    const range = dateRangeFromToday(30);
    Promise.all([
      haruApi.getMyTutorProfile(accessToken).catch(() => null),
      haruApi.getMySchedule(accessToken, range.from, range.to).catch(() => ({ slots: [] }))
    ])
      .then(([nextProfile, schedule]) => {
        setProfile(nextProfile);
        setSlots(schedule.slots);
      })
      .catch((err: Error) => setError(err.message));
  }, [accessToken]);

  const approved = (profile?.status ?? user?.tutorProfileStatus) === "APPROVED";
  const completion = [profile?.displayName, profile?.shortIntroduction, profile?.aboutMe, profile?.whatIOffer, slots.length > 0]
    .filter(Boolean).length;

  return (
    <main className="dashboard-layout">
      <Sidebar />
      <section className="dashboard-main">
        <header className="dashboard-head">
          <div>
            <Badge tone={approved ? "green" : "orange"}>{statusLabel(profile?.status ?? user?.tutorProfileStatus)}</Badge>
            <h1>튜터 대시보드</h1>
            <p>오늘 수업, 공개 일정, 프로필 상태와 예상 수익을 빠르게 확인하세요.</p>
          </div>
          <div className="dashboard-user">
            <Avatar label={user?.name?.slice(0, 1) ?? "A"} />
            <strong>{user?.name ?? "튜터"}</strong>
          </div>
        </header>
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        {!accessToken ? <EmptyState title="로그인이 필요합니다" body="튜터 대시보드는 로그인 후 사용할 수 있습니다." /> : null}

        <div className="stats-grid">
          <StatCard label="오늘 수업" value="0" hint="예약 API 연동 예정" />
          <StatCard label="이번 주 예약" value={String(slots.length)} hint="공개된 수업 가능 시간" />
          <StatCard label="완료 수업" value="0" hint="완료 예약 집계 예정" />
          <StatCard label="예상 수익" value="USD 0.00" hint="정산 API 연동 예정" />
        </div>

        <section className="panel next-action-card">
          <div className="prep-icon">
            <FileCheck2 size={24} />
          </div>
          <div>
            <Badge tone="brand">다음 할 일</Badge>
            <h2>{approved ? "이번 주 가능한 수업 시간을 추가하세요" : "프로필 승인 상태를 확인하세요"}</h2>
            <p>
              {approved
                ? "학생은 공개된 시간에서만 예약할 수 있습니다. 비어 있는 시간대를 먼저 채워두면 예약 전환이 높아집니다."
                : "프로필 소개, 수업 내용, 언어, 가격을 채운 뒤 관리자 승인을 요청하세요."}
            </p>
          </div>
          <Button as="a" href={approved ? "/tutor/schedule" : "/tutor/profile"}>
            {approved ? "일정 관리" : "프로필 완성"}
          </Button>
        </section>

        <div className="dashboard-grid">
          <section className="panel dashboard-panel">
            <SectionHeader eyebrow="Schedule" title="다가오는 가능 시간" action={<Button as="a" href="/tutor/schedule" variant="secondary">관리</Button>} />
            {slots.length === 0 ? <EmptyState title="등록된 시간이 없습니다" body="일정 관리에서 수업 가능한 시간을 등록하세요." /> : null}
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
            <SectionHeader eyebrow="Profile" title="프로필 완성도" description={`${completion}/5 항목 완료`} />
            <div className="progress-track">
              <span style={{ width: `${(completion / 5) * 100}%` }} />
            </div>
            <div className="info-list">
              <p><strong>표시 이름</strong><span>{profile?.displayName ?? "-"}</span></p>
              <p><strong>한 줄 소개</strong><span>{profile?.shortIntroduction ?? "-"}</span></p>
              <p><strong>언어</strong><span>{profile?.availableLanguages?.join(", ") ?? "-"}</span></p>
            </div>
            <Button as="a" href="/tutor/profile" className="wide">프로필 관리</Button>
          </section>

          <section className="panel mini-dashboard-card">
            <CheckCircle2 size={22} />
            <strong>완료 수업</strong>
            <span>정산 가능한 완료 수업이 여기에 표시됩니다.</span>
          </section>
          <section className="panel mini-dashboard-card">
            <DollarSign size={22} />
            <strong>수익 관리</strong>
            <span>예상 수익과 정산 상태를 한눈에 확인합니다.</span>
          </section>
        </div>
      </section>
    </main>
  );
}
