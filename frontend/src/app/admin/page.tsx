"use client";

import { AlertTriangle, BarChart3, CreditCard, ShieldCheck, Users } from "lucide-react";
import { useState } from "react";
import { haruApi } from "../api";
import { useAuth } from "../auth";
import { adminRows } from "../data";
import { ApiNotice, Badge, Button, Field, SectionHeader, Sidebar, StatCard } from "../components";

export default function AdminDashboardPage() {
  const { accessToken, user } = useAuth();
  const [tutorProfileId, setTutorProfileId] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function decide(action: "approve" | "reject") {
    if (!accessToken) {
      setError("관리자 계정으로 로그인해주세요.");
      return;
    }

    const id = Number(tutorProfileId);
    if (!Number.isInteger(id) || id <= 0) {
      setError("튜터 프로필 ID를 1 이상의 숫자로 입력해주세요.");
      return;
    }

    setError(null);
    setMessage(null);
    try {
      const profile = action === "approve" ? await haruApi.approveTutor(accessToken, id) : await haruApi.rejectTutor(accessToken, id);
      setMessage(`${profile.displayName ?? `프로필 #${profile.id}`} 처리 완료: ${profile.status}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "관리자 처리에 실패했습니다.");
    }
  }

  return (
    <main className="dashboard-layout">
      <Sidebar admin />
      <section className="dashboard-main">
        <header className="admin-title">
          <div>
            <Badge tone="blue">Admin</Badge>
            <h1>관리자 대시보드</h1>
            <p>회원, 튜터 승인, 예약, 결제/정산, 신고/후기, 통계를 빠르게 판단할 수 있게 구성했습니다.</p>
          </div>
          <button>{user?.activeRole ?? "로그인 필요"}</button>
        </header>
        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}

        <section className="panel admin-action-card">
          <div>
            <Badge tone="orange">승인 대기</Badge>
            <h2>튜터 승인 관리</h2>
            <p>새 튜터 프로필을 검토하고 승인 또는 반려 처리합니다. 상세 목록에서는 API의 실제 PENDING 데이터를 불러옵니다.</p>
          </div>
          <Button as="a" href="/admin/tutors">
            승인 대기 목록 열기
          </Button>
        </section>

        <div className="stats-grid admin">
          <StatCard label="전체 회원" value="12,458" hint="모의 지표" />
          <StatCard label="신규 회원" value="842" hint="이번 달" />
          <StatCard label="예약 수업" value="2,153" hint="진행 예정 포함" />
          <StatCard label="완료 수업" value="1,782" hint="정산 대상" />
          <StatCard label="결제 금액" value="USD 34,250" hint="모의 지표" />
          <StatCard label="정산 대기" value="USD 8,320" hint="튜터 지급 예정" />
        </div>

        <div className="admin-grid">
          <section className="panel chart-panel">
            <SectionHeader eyebrow="Analytics" title="예약 수업 추이" />
            <div className="line-chart" aria-label="최근 예약 추이">
              {[35, 56, 72, 49, 40, 38, 52].map((height, index) => (
                <span style={{ height: `${height}%` }} key={index} />
              ))}
            </div>
          </section>

          <section className="panel alert-panel">
            <SectionHeader eyebrow="Quick Action" title="빠른 승인 처리" />
            <Field label="튜터 프로필 ID">
              <input value={tutorProfileId} onChange={(event) => setTutorProfileId(event.target.value)} placeholder="예: 1" inputMode="numeric" />
            </Field>
            <div className="button-row">
              <Button onClick={() => void decide("approve")}>
                <ShieldCheck size={16} /> 승인
              </Button>
              <Button variant="secondary" onClick={() => void decide("reject")}>
                반려
              </Button>
            </div>
            <Button as="a" href="/admin/tutors" variant="ghost" className="wide">승인 대기 목록 보기</Button>
          </section>

          <section className="panel admin-menu-grid">
            {[
              ["회원 관리", Users, "가입, 역할, 계정 상태"],
              ["결제/정산", CreditCard, "결제 상태와 튜터 정산"],
              ["신고/후기", AlertTriangle, "신고 접수와 후기 모니터링"],
              ["통계", BarChart3, "전환율과 수업 지표"]
            ].map(([title, Icon, body]) => (
              <article className="admin-menu-card" key={title as string}>
                <Icon size={21} />
                <strong>{title as string}</strong>
                <span>{body as string}</span>
              </article>
            ))}
          </section>

          <section className="panel tutor-approval">
            <SectionHeader eyebrow="Review Queue" title="승인 업무 예시" />
            <table>
              <thead>
                <tr>
                  <th>이름</th>
                  <th>이메일</th>
                  <th>경력</th>
                  <th>신청일</th>
                  <th>상태</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {adminRows.map((row) => (
                  <tr key={row.email}>
                    <td>{row.name}</td>
                    <td>{row.email}</td>
                    <td>{row.career}</td>
                    <td>{row.submitted}</td>
                    <td><Badge tone="orange">{row.status}</Badge></td>
                    <td><Button as="a" href="/admin/tutors" variant="secondary">검토</Button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </div>
      </section>
    </main>
  );
}
