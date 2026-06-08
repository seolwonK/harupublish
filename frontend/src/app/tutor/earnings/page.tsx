"use client";

import { ArrowDownLeft, ArrowUpRight, Banknote, RefreshCcw, Wallet } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  formatMoney,
  formatRatePercent,
  haruApi,
  type MonthlySettlementResponse,
  type TutorEarningLedgerEntry,
  type TutorEarningsResponse,
  type WithdrawalMethod,
  type WithdrawalResponse
} from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, Badge, Button, EmptyState, Field, SectionHeader, Sidebar, StatCard, statusLabel } from "../../components";

// 사이버머니/정산 잔액은 항상 USD 고정 표기 (전역 통화토글 무시).
function usd(value: number | string | null | undefined) {
  return formatMoney(value, { currency: "USD" });
}

const METHOD_LABELS: Record<WithdrawalMethod, string> = {
  PAYPAL: "PayPal (3%)",
  PAYONEER: "Payoneer (3%)",
  DOMESTIC_BANK: "국내 계좌 (5%)"
};

// 원장 entryType 라벨 (BE가 새 타입을 보내도 깨지지 않게 기본값 fallback).
const LEDGER_ENTRY_LABELS: Record<string, string> = {
  LESSON_EARNED: "수업 적립",
  NO_SHOW_EARNED: "노쇼 적립",
  HOLD: "인출 HOLD",
  REVERSED: "인출 환원",
  WITHDRAWN: "인출 차감",
  PROMO_PAYBACK: "프로모 페이백",
  ADJUSTMENT: "조정"
};

function ledgerEntryLabel(entryType: string) {
  return LEDGER_ENTRY_LABELS[entryType] ?? entryType;
}

// 적립(+)인지 차감(-)인지 판별 — 금액 부호 우선, 없으면 타입으로 추정.
function isCreditEntry(entry: TutorEarningLedgerEntry) {
  const amount = Number(entry.amount);
  if (Number.isFinite(amount) && amount !== 0) return amount > 0;
  return entry.entryType === "LESSON_EARNED" || entry.entryType === "NO_SHOW_EARNED" || entry.entryType === "REVERSED" || entry.entryType === "PROMO_PAYBACK";
}

function ledgerTone(entry: TutorEarningLedgerEntry) {
  if (entry.entryType === "HOLD") return "orange" as const;
  if (entry.entryType === "REVERSED") return "blue" as const;
  return isCreditEntry(entry) ? "green" as const : "red" as const;
}

function withdrawalStatusTone(status: WithdrawalResponse["status"]) {
  if (status === "PAID") return "green" as const;
  if (status === "REJECTED" || status === "REVERSED") return "red" as const;
  if (status === "APPROVED") return "blue" as const;
  return "orange" as const;
}

export default function TutorEarningsPage() {
  const { accessToken } = useAuth();
  const [earnings, setEarnings] = useState<TutorEarningsResponse | null>(null);
  const [withdrawals, setWithdrawals] = useState<WithdrawalResponse[]>([]);
  const [settlements, setSettlements] = useState<MonthlySettlementResponse[]>([]);
  const [method, setMethod] = useState<WithdrawalMethod>("PAYPAL");
  const [amount, setAmount] = useState("");
  const [payoutAccount, setPayoutAccount] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setError(null);
    try {
      const [earningsResponse, withdrawalResponse, settlementResponse] = await Promise.all([
        haruApi.getMyEarnings(accessToken),
        haruApi.getMyWithdrawals(accessToken).catch(() => ({ withdrawals: [] })),
        haruApi.getMySettlements(accessToken).catch(() => ({ settlements: [] }))
      ]);
      setEarnings(earningsResponse);
      setWithdrawals(withdrawalResponse.withdrawals);
      setSettlements(settlementResponse.settlements);
    } catch (err) {
      setError(err instanceof Error ? err.message : "수익 정보를 불러오지 못했습니다.");
    }
  }, [accessToken]);

  useEffect(() => {
    void load();
  }, [load]);

  const available = earnings?.availableBalanceAmount ?? 0;

  // 인출 수수료 미리보기 (요율은 백엔드 권위, FE는 안내용).
  const previewFeeRate = method === "DOMESTIC_BANK" ? 0.05 : 0.03;
  const previewNet = useMemo(() => {
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) return null;
    return value - Math.round(value * previewFeeRate * 100) / 100;
  }, [amount, previewFeeRate]);

  async function requestWithdrawal() {
    if (!accessToken) return;
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) {
      setError("인출 금액을 0보다 큰 숫자로 입력해주세요.");
      return;
    }
    if (!payoutAccount.trim()) {
      setError("지급 계좌/이메일을 입력해주세요.");
      return;
    }
    setSubmitting(true);
    setError(null);
    setMessage(null);
    try {
      await haruApi.createWithdrawal(accessToken, { method, amount: value, payoutAccount: payoutAccount.trim() });
      setMessage("인출 요청이 접수되었습니다. 관리자 승인 후 지급됩니다.");
      setAmount("");
      setPayoutAccount("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "인출 요청에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="dashboard-layout">
      <Sidebar />
      <section className="dashboard-main">
        <header className="dashboard-head">
          <div>
            <Badge tone="brand">Earnings</Badge>
            <h1>수익 관리</h1>
            <p>완료된 수업에서 플랫폼 수수료 15%를 제외한 순수익이 사이버머니(USD)로 적립됩니다. 잔액·원장은 USD 고정 표기입니다.</p>
          </div>
          <div className="dashboard-head-actions">
            <span className="usd-fixed-note">USD 고정 표기</span>
            <Button variant="ghost" onClick={() => void load()} disabled={!accessToken}>
              <RefreshCcw size={16} /> 새로고침
            </Button>
          </div>
        </header>

        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        {!accessToken ? <EmptyState title="튜터 로그인이 필요합니다" body="튜터 계정으로 로그인하면 수익과 정산을 확인할 수 있습니다." /> : null}

        <div className="stats-grid">
          <StatCard label="인출 가능 잔액" value={usd(available)} hint="사이버머니 (USD)" />
          <StatCard label="누적 적립" value={usd(earnings?.totalEarnedAmount)} hint="15% 차감 후" />
          <StatCard label="인출 진행 중" value={usd(earnings?.pendingWithdrawalAmount)} hint="HOLD 상태" />
          <StatCard label="누적 인출" value={usd(earnings?.totalWithdrawnAmount)} hint="지급 완료 포함" />
        </div>

        <section className="panel">
          <SectionHeader
            eyebrow="Ledger"
            title="사이버머니 원장"
            description="수업 적립 · 인출 HOLD · 환원 등 잔액 변동 내역입니다. 적립액은 플랫폼 수수료 15% 차감 후 금액(USD 액면)입니다."
          />
          {!earnings || earnings.ledger.length === 0 ? (
            <EmptyState title="원장 내역이 없습니다" body="수업이 완료되어 정산되면 적립 내역이 이곳에 쌓입니다." />
          ) : (
            <div className="payment-list">
              {earnings.ledger.map((entry) => {
                const credit = isCreditEntry(entry);
                return (
                  <article className="payment-card" key={entry.id}>
                    <div className="payment-icon">
                      {credit ? <ArrowDownLeft size={22} /> : <ArrowUpRight size={22} />}
                    </div>
                    <div>
                      <strong>{ledgerEntryLabel(entry.entryType)}</strong>
                      <p>
                        {entry.bookingId ? `수업 #${entry.bookingId}` : null}
                        {entry.bookingId && entry.withdrawalId ? " · " : null}
                        {entry.withdrawalId ? `인출 #${entry.withdrawalId}` : null}
                        {!entry.bookingId && !entry.withdrawalId ? (entry.memo ?? "잔액 변동") : null}
                      </p>
                      <span>{new Date(entry.createdAt).toLocaleString("ko-KR")}</span>
                      {entry.entryType === "LESSON_EARNED" ? <span className="usd-fixed-note">플랫폼 수수료 15% 차감 후 적립</span> : null}
                      {entry.memo && (entry.bookingId || entry.withdrawalId) ? <span>{entry.memo}</span> : null}
                    </div>
                    <div className="payment-amount">
                      <strong className={credit ? "ledger-amount-plus" : "ledger-amount-minus"}>
                        {credit ? "+" : "−"}
                        {usd(Math.abs(Number(entry.amount)))}
                      </strong>
                      <span className="fee-caption">잔액 {usd(entry.balanceAfter)}</span>
                    </div>
                    <Badge tone={ledgerTone(entry)}>{ledgerEntryLabel(entry.entryType)}</Badge>
                  </article>
                );
              })}
            </div>
          )}
        </section>

        <section className="panel">
          <SectionHeader eyebrow="Withdraw" title="인출 요청" description="PayPal·Payoneer 3% / 국내 계좌 5% 수수료가 적용됩니다. 인출 시 잔액에서 즉시 HOLD 처리됩니다." />
          <div className="form-grid">
            <Field label="인출 수단">
              <select value={method} onChange={(event) => setMethod(event.target.value as WithdrawalMethod)}>
                <option value="PAYPAL">{METHOD_LABELS.PAYPAL}</option>
                <option value="PAYONEER">{METHOD_LABELS.PAYONEER}</option>
                <option value="DOMESTIC_BANK">{METHOD_LABELS.DOMESTIC_BANK}</option>
              </select>
            </Field>
            <Field label="인출 금액 (USD)" hint={previewNet !== null ? `예상 수수료 ${formatRatePercent(previewFeeRate)} · 실수령 ${usd(previewNet)}` : undefined}>
              <input value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="예: 100" inputMode="decimal" />
            </Field>
            <Field label="지급 계좌 / 이메일">
              <input value={payoutAccount} onChange={(event) => setPayoutAccount(event.target.value)} placeholder="PayPal 이메일 또는 계좌번호" />
            </Field>
          </div>
          <div className="button-row">
            <Button onClick={() => void requestWithdrawal()} disabled={submitting || !accessToken}>
              <Banknote size={16} /> {submitting ? "요청 중..." : "인출 요청"}
            </Button>
          </div>
        </section>

        <section className="panel">
          <SectionHeader eyebrow="Withdrawals" title="인출 내역" />
          {withdrawals.length === 0 ? (
            <EmptyState title="인출 내역이 없습니다" body="첫 인출을 요청하면 이곳에서 상태를 추적할 수 있습니다." />
          ) : (
            <div className="payment-list">
              {withdrawals.map((withdrawal) => (
                <article className="payment-card" key={withdrawal.id}>
                  <div className="payment-icon">
                    <Wallet size={22} />
                  </div>
                  <div>
                    <strong>{METHOD_LABELS[withdrawal.method]}</strong>
                    <p>
                      요청 {usd(withdrawal.requestedAmount)} · 수수료 {usd(withdrawal.feeAmount)} ({formatRatePercent(withdrawal.feeRate)}) · 실수령 {usd(withdrawal.netAmount)}
                    </p>
                    <span>{new Date(withdrawal.requestedAt).toLocaleString("ko-KR")}</span>
                    {withdrawal.promoPaybackPending ? <span className="usd-fixed-note">프로모 면제 페이백 대기</span> : null}
                    {withdrawal.rejectReason ? <span>반려 사유: {withdrawal.rejectReason}</span> : null}
                  </div>
                  <Badge tone={withdrawalStatusTone(withdrawal.status)}>{statusLabel(withdrawal.status)}</Badge>
                </article>
              ))}
            </div>
          )}
        </section>

        <section className="panel">
          <SectionHeader eyebrow="Settlements" title="월 정산" description="월별 gross / 플랫폼 수수료(15%) / 순수익 집계입니다." />
          {settlements.length === 0 ? (
            <EmptyState title="정산 내역이 없습니다" body="수업이 완료되면 월별 정산이 집계됩니다." />
          ) : (
            <table>
              <thead>
                <tr>
                  <th>기간</th>
                  <th>수업 수</th>
                  <th>Gross</th>
                  <th>수수료</th>
                  <th>순수익</th>
                  <th>상태</th>
                </tr>
              </thead>
              <tbody>
                {settlements.map((settlement) => (
                  <tr key={settlement.id}>
                    <td>{settlement.year}년 {settlement.month}월</td>
                    <td>{settlement.lessonCount}</td>
                    <td>{usd(settlement.grossAmount)}</td>
                    <td>{usd(settlement.platformFeeAmount)}</td>
                    <td>{usd(settlement.netAmount)}</td>
                    <td><Badge tone="blue">{statusLabel(settlement.status)}</Badge></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </section>

      <style>{`
        .dashboard-head-actions {
          display: flex;
          align-items: center;
          gap: 14px;
          flex-wrap: wrap;
          justify-content: flex-end;
        }
        .ledger-amount-plus {
          color: #15803d;
        }
        .ledger-amount-minus {
          color: #b91c1c;
        }
      `}</style>
    </main>
  );
}
