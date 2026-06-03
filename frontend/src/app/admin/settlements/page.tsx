"use client";

import { ArrowRightLeft, CreditCard } from "lucide-react";
import { useState } from "react";
import {
  formatMoney,
  haruApi,
  type MonthlySettlementResponse,
  type SettlementStatus
} from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, Badge, Button, EmptyState, Field, Sidebar, statusLabel } from "../../components";

// 월 정산 잔액은 항상 USD 고정 표기 (전역 통화토글 무시).
function usd(value: number | string | null | undefined) {
  return formatMoney(value, { currency: "USD" });
}

// 정산 상태 전이 흐름: OPEN(집계 중) → CLOSED(마감) → FINALIZED(확정) → PAID(지급 완료).
const STATUS_FLOW: SettlementStatus[] = ["OPEN", "CLOSED", "FINALIZED", "PAID"];

function statusTone(status: SettlementStatus) {
  if (status === "PAID") return "green" as const;
  if (status === "FINALIZED") return "blue" as const;
  if (status === "CLOSED") return "orange" as const;
  return "neutral" as const;
}

export default function AdminSettlementsPage() {
  const { accessToken } = useAuth();
  const [settlementId, setSettlementId] = useState("");
  const [targetStatus, setTargetStatus] = useState<SettlementStatus>("CLOSED");
  const [result, setResult] = useState<MonthlySettlementResponse | null>(null);
  const [history, setHistory] = useState<MonthlySettlementResponse[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function transition() {
    if (!accessToken) {
      setError("관리자 계정으로 로그인해주세요.");
      return;
    }
    const id = Number(settlementId);
    if (!Number.isInteger(id) || id <= 0) {
      setError("정산 ID는 1 이상의 숫자로 입력해주세요.");
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await haruApi.updateSettlementStatus(accessToken, id, targetStatus);
      setResult(updated);
      setHistory((current) => [updated, ...current.filter((item) => item.id !== updated.id)].slice(0, 20));
      setMessage(`정산 #${updated.id} (${updated.year}년 ${updated.month}월) 상태를 ${statusLabel(updated.status)}(으)로 전이했습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "정산 상태 전이에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="dashboard-layout">
      <Sidebar admin />
      <section className="dashboard-main">
        <header className="admin-title">
          <div>
            <Badge tone="blue">Settlements</Badge>
            <h1>월 정산 관리</h1>
            <p>
              월별 정산의 상태를 전이합니다. 흐름은 OPEN(집계 중) → CLOSED(마감) → FINALIZED(확정) → PAID(지급 완료)이며, 금액은 진실 원장 USD 기준으로
              표기됩니다.
            </p>
          </div>
          <span className="usd-fixed-note">USD 고정 표기</span>
        </header>

        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        {!accessToken ? (
          <EmptyState title="관리자 로그인이 필요합니다" body="ADMIN 계정으로 로그인하면 월 정산 상태를 전이할 수 있습니다." />
        ) : null}

        <section className="panel">
          <div className="section-title-row">
            <h2>상태 전이</h2>
            <span className="usd-fixed-note">{STATUS_FLOW.join(" → ")}</span>
          </div>
          <p>
            정산 ID와 목표 상태를 입력하면 해당 정산의 상태를 전이합니다. 정산 ID는 튜터 수익 화면(월 정산)·관리자 정산 집계에서 확인할 수 있습니다.
          </p>
          <div className="form-grid">
            <Field label="정산 ID">
              <input
                value={settlementId}
                onChange={(event) => setSettlementId(event.target.value)}
                placeholder="예: 42"
                inputMode="numeric"
              />
            </Field>
            <Field label="목표 상태">
              <select value={targetStatus} onChange={(event) => setTargetStatus(event.target.value as SettlementStatus)}>
                {STATUS_FLOW.map((status) => (
                  <option key={status} value={status}>
                    {statusLabel(status)} ({status})
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <div className="button-row">
            <Button onClick={() => void transition()} disabled={busy || !accessToken}>
              <ArrowRightLeft size={16} /> {busy ? "전이 중..." : "상태 전이"}
            </Button>
          </div>
        </section>

        {result ? (
          <section className="panel">
            <div className="section-title-row">
              <h2>방금 처리한 정산</h2>
              <Badge tone={statusTone(result.status)}>{statusLabel(result.status)}</Badge>
            </div>
            <div className="stats-grid">
              <article className="stat-card">
                <p>기간</p>
                <strong>
                  {result.year}년 {result.month}월
                </strong>
                <span>수업 {result.lessonCount}회</span>
              </article>
              <article className="stat-card">
                <p>Gross</p>
                <strong>{usd(result.grossAmount)}</strong>
                <span>튜터 gross 합계</span>
              </article>
              <article className="stat-card">
                <p>플랫폼 수수료</p>
                <strong>{usd(result.platformFeeAmount)}</strong>
                <span>15% 차감</span>
              </article>
              <article className="stat-card">
                <p>순수익</p>
                <strong>{usd(result.netAmount)}</strong>
                <span>튜터 적립액</span>
              </article>
            </div>
          </section>
        ) : null}

        <section className="panel">
          <div className="section-title-row">
            <h2>이번 세션 처리 내역</h2>
            <CreditCard size={16} aria-hidden />
          </div>
          {history.length === 0 ? (
            <EmptyState title="처리 내역이 없습니다" body="정산 상태를 전이하면 이번 세션에서 처리한 항목이 이곳에 누적됩니다." />
          ) : (
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>기간</th>
                  <th>수업 수</th>
                  <th>Gross</th>
                  <th>수수료</th>
                  <th>순수익</th>
                  <th>상태</th>
                </tr>
              </thead>
              <tbody>
                {history.map((settlement) => (
                  <tr key={settlement.id}>
                    <td>#{settlement.id}</td>
                    <td>
                      {settlement.year}년 {settlement.month}월
                    </td>
                    <td>{settlement.lessonCount}</td>
                    <td>{usd(settlement.grossAmount)}</td>
                    <td>{usd(settlement.platformFeeAmount)}</td>
                    <td>{usd(settlement.netAmount)}</td>
                    <td>
                      <Badge tone={statusTone(settlement.status)}>{statusLabel(settlement.status)}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </section>
    </main>
  );
}
