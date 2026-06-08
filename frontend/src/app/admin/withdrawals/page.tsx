"use client";

import { Banknote, Check, RefreshCcw, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import {
  formatMoney,
  formatRatePercent,
  haruApi,
  type WithdrawalMethod,
  type WithdrawalResponse,
  type WithdrawalStatus
} from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, Badge, Button, EmptyState, Sidebar, statusLabel } from "../../components";

// 인출 금액은 USD 액면 고정 표기.
function usd(value: number | string | null | undefined) {
  return formatMoney(value, { currency: "USD" });
}

const METHOD_LABELS: Record<WithdrawalMethod, string> = {
  PAYPAL: "PayPal",
  PAYONEER: "Payoneer",
  DOMESTIC_BANK: "국내 계좌"
};

const STATUS_FILTERS: Array<{ label: string; value: WithdrawalStatus | "ALL" }> = [
  { label: "전체", value: "ALL" },
  { label: "요청됨", value: "REQUESTED" },
  { label: "승인됨", value: "APPROVED" },
  { label: "지급완료", value: "PAID" },
  { label: "반려됨", value: "REJECTED" }
];

function statusTone(status: WithdrawalStatus) {
  if (status === "PAID") return "green" as const;
  if (status === "REJECTED" || status === "REVERSED") return "red" as const;
  if (status === "APPROVED") return "blue" as const;
  return "orange" as const;
}

export default function AdminWithdrawalsPage() {
  const { accessToken } = useAuth();
  const [withdrawals, setWithdrawals] = useState<WithdrawalResponse[]>([]);
  const [filter, setFilter] = useState<WithdrawalStatus | "ALL">("ALL");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setError(null);
    try {
      const response = await haruApi.getAdminWithdrawals(accessToken, filter === "ALL" ? undefined : filter);
      setWithdrawals(response.withdrawals);
    } catch (err) {
      setError(err instanceof Error ? err.message : "인출 요청 목록을 불러오지 못했습니다.");
    }
  }, [accessToken, filter]);

  useEffect(() => {
    void load();
  }, [load]);

  async function act(id: number, action: "approve" | "paid" | "reject") {
    if (!accessToken) return;
    setBusyId(id);
    setError(null);
    setMessage(null);
    try {
      if (action === "approve") {
        await haruApi.approveWithdrawal(accessToken, id);
        setMessage(`인출 #${id} 승인 처리되었습니다.`);
      } else if (action === "paid") {
        await haruApi.markWithdrawalPaid(accessToken, id);
        setMessage(`인출 #${id} 지급 완료로 표시되었습니다.`);
      } else {
        const reason = window.prompt("반려 사유를 입력하세요.") ?? "";
        if (!reason.trim()) {
          setBusyId(null);
          return;
        }
        await haruApi.rejectWithdrawal(accessToken, id, reason.trim());
        setMessage(`인출 #${id} 반려되어 잔액이 환원되었습니다.`);
      }
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "인출 처리에 실패했습니다.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <main className="dashboard-layout">
      <Sidebar admin />
      <section className="dashboard-main">
        <header className="admin-title">
          <div>
            <Badge tone="blue">Withdrawals</Badge>
            <h1>인출 관리</h1>
            <p>튜터 인출 요청을 검토하고 승인 → 지급 완료 처리하거나 반려합니다. 반려 시 HOLD 금액이 잔액으로 환원됩니다.</p>
          </div>
          <Button variant="ghost" onClick={() => void load()}>
            <RefreshCcw size={16} /> 새로고침
          </Button>
        </header>

        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        {!accessToken ? <EmptyState title="관리자 로그인이 필요합니다" body="ADMIN 계정으로 로그인하면 인출 요청을 관리할 수 있습니다." /> : null}

        <div className="currency-toggle" role="group" aria-label="상태 필터" style={{ flexWrap: "wrap" }}>
          {STATUS_FILTERS.map((option) => (
            <button
              type="button"
              key={option.value}
              className={`currency-toggle-button ${filter === option.value ? "selected" : ""}`}
              onClick={() => setFilter(option.value)}
              aria-pressed={filter === option.value}
            >
              {option.label}
            </button>
          ))}
        </div>

        <section className="panel">
          {withdrawals.length === 0 ? (
            <EmptyState title="인출 요청이 없습니다" body="선택한 상태의 인출 요청이 없습니다." />
          ) : (
            <div className="payment-list">
              {withdrawals.map((withdrawal) => (
                <article className="payment-card" key={withdrawal.id}>
                  <div className="payment-icon">
                    <Banknote size={22} />
                  </div>
                  <div>
                    <strong>튜터 #{withdrawal.tutorProfileId} · {METHOD_LABELS[withdrawal.method]}</strong>
                    <p>
                      요청 {usd(withdrawal.requestedAmount)} · 수수료 {usd(withdrawal.feeAmount)} ({formatRatePercent(withdrawal.feeRate)}) · 실지급 {usd(withdrawal.netAmount)}
                    </p>
                    <span>{new Date(withdrawal.requestedAt).toLocaleString("ko-KR")}</span>
                    {withdrawal.payoutAccount ? <span>지급처: {withdrawal.payoutAccount}</span> : null}
                    {withdrawal.promoPaybackPending ? <span className="usd-fixed-note">프로모 면제 페이백 대기</span> : null}
                    {withdrawal.rejectReason ? <span>반려 사유: {withdrawal.rejectReason}</span> : null}
                  </div>
                  <Badge tone={statusTone(withdrawal.status)}>{statusLabel(withdrawal.status)}</Badge>
                  <div className="payment-actions">
                    {withdrawal.status === "REQUESTED" ? (
                      <>
                        <Button variant="secondary" onClick={() => void act(withdrawal.id, "approve")} disabled={busyId === withdrawal.id}>
                          <Check size={14} /> 승인
                        </Button>
                        <Button variant="ghost" onClick={() => void act(withdrawal.id, "reject")} disabled={busyId === withdrawal.id}>
                          <X size={14} /> 반려
                        </Button>
                      </>
                    ) : null}
                    {withdrawal.status === "APPROVED" ? (
                      <Button onClick={() => void act(withdrawal.id, "paid")} disabled={busyId === withdrawal.id}>
                        <Check size={14} /> 지급 완료
                      </Button>
                    ) : null}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </section>
    </main>
  );
}
