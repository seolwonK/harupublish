"use client";

import { ArrowDownLeft, ArrowUpRight, CalendarClock, Gift, RefreshCcw, Sparkles } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { formatMoney, haruApi, type HaruCreditLedgerEntry, type HaruCreditResponse } from "../api";
import { useAuth } from "../auth";
import { ApiNotice, AppHeader, Badge, Button, EmptyState, SectionHeader, StatCard } from "../components";

// Haru Credits 잔액·원장은 항상 USD 고정 표기 (전역 통화토글 무시).
function usd(value: number | string | null | undefined) {
  return formatMoney(value, { currency: "USD" });
}

// 원장 entryType 라벨 (BE가 새 타입을 보내도 깨지지 않게 fallback).
const ENTRY_LABELS: Record<string, string> = {
  REFUND_ISSUED: "환불 적립",
  SPENT: "크레딧 사용",
  EXPIRED: "만료 차감"
};

function entryLabel(entryType: string) {
  return ENTRY_LABELS[entryType] ?? entryType;
}

// 적립(+)인지 차감(-)인지: 금액 부호 우선, 없으면 타입으로 추정.
function isCreditEntry(entry: HaruCreditLedgerEntry) {
  const amount = Number(entry.amount);
  if (Number.isFinite(amount) && amount !== 0) return amount > 0;
  return entry.entryType === "REFUND_ISSUED";
}

function entryTone(entryType: string) {
  if (entryType === "REFUND_ISSUED") return "green" as const;
  if (entryType === "EXPIRED") return "red" as const;
  return "blue" as const;
}

function formatExpiry(value: string | null) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  const days = Math.ceil((date.getTime() - Date.now()) / (1000 * 60 * 60 * 24));
  return {
    label: date.toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric" }),
    days,
    expired: days < 0
  };
}

export default function CreditsPage() {
  const { accessToken } = useAuth();
  const [credit, setCredit] = useState<HaruCreditResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setError(null);
    try {
      const response = await haruApi.getMyCredits(accessToken);
      setCredit(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "크레딧 정보를 불러오지 못했습니다.");
    } finally {
      setLoaded(true);
    }
  }, [accessToken]);

  useEffect(() => {
    void load();
  }, [load]);

  const ledger = credit?.ledger ?? [];
  const totalRefunded = ledger
    .filter((entry) => entry.entryType === "REFUND_ISSUED")
    .reduce((sum, entry) => sum + Math.abs(Number(entry.amount) || 0), 0);
  const expiry = formatExpiry(credit?.expiresAt ?? null);

  if (!accessToken) {
    return (
      <main className="page-shell">
        <AppHeader />
        <div className="booking-empty-panel">
          <EmptyState title="로그인이 필요합니다" body="Haru Credits 잔액과 사용 내역을 보려면 먼저 로그인해주세요." />
          <Button as="a" href="/login">
            로그인하기
          </Button>
        </div>
      </main>
    );
  }

  return (
    <main className="page-shell compact">
      <AppHeader />

      <section className="payments-layout">
        <aside className="summary-rail">
          <SectionHeader eyebrow="Haru Credits" title="크레딧 요약" />
          <StatCard label="사용 가능 잔액" value={usd(credit?.balanceAmount)} hint="USD 고정" />
          <StatCard label="누적 환불 적립" value={usd(totalRefunded)} hint="환불로 발급된 크레딧" />
          <StatCard
            label="만료 예정"
            value={expiry ? expiry.label : "—"}
            hint={expiry ? (expiry.expired ? "만료됨" : `약 ${expiry.days}일 남음`) : "계정 12개월 비활성 기준"}
          />
          <div className="credit-note">
            <Sparkles size={15} />
            <p>Haru Credits는 USD로만 표기되며, 환불 승인 시 자동 적립됩니다. 결제 시 잔액에서 우선 차감됩니다.</p>
          </div>
        </aside>

        <section className="panel payments-panel">
          <SectionHeader
            eyebrow="Ledger"
            title="크레딧 내역"
            description="환불 적립 · 사용 · 만료 등 크레딧 변동 내역입니다. 모든 금액은 USD 액면입니다."
            action={
              <Button variant="ghost" onClick={() => void load()}>
                <RefreshCcw size={14} /> 새로고침
              </Button>
            }
          />

          {error ? <ApiNotice type="error">{error}</ApiNotice> : null}

          {expiry && !expiry.expired && expiry.days <= 30 ? (
            <ApiNotice type="info">
              <CalendarClock size={14} /> 크레딧이 {expiry.label}에 만료됩니다. 만료 전에 사용해주세요.
            </ApiNotice>
          ) : null}
          {expiry?.expired ? <ApiNotice type="error">크레딧 만료일이 지났습니다. 잔액을 확인해주세요.</ApiNotice> : null}

          {loaded && ledger.length === 0 ? (
            <EmptyState
              title="크레딧 내역이 없습니다"
              body="환불이 승인되면 Haru Credits로 적립되어 이곳에 표시됩니다. 다음 결제 시 잔액에서 우선 차감됩니다."
            />
          ) : null}

          <div className="payment-list">
            {ledger.map((entry) => {
              const credited = isCreditEntry(entry);
              return (
                <article className="payment-card" key={entry.id}>
                  <div className="payment-icon">
                    {entry.entryType === "REFUND_ISSUED" ? (
                      <Gift size={22} />
                    ) : credited ? (
                      <ArrowDownLeft size={22} />
                    ) : (
                      <ArrowUpRight size={22} />
                    )}
                  </div>
                  <div>
                    <strong>{entryLabel(entry.entryType)}</strong>
                    <p>{entry.paymentId ? `결제 #${entry.paymentId}` : entry.memo ?? "크레딧 변동"}</p>
                    <span>{new Date(entry.createdAt).toLocaleString("ko-KR")}</span>
                    {entry.memo && entry.paymentId ? <span>{entry.memo}</span> : null}
                  </div>
                  <div className="payment-amount">
                    <strong className={credited ? "credit-amount-plus" : "credit-amount-minus"}>
                      {credited ? "+" : "−"}
                      {usd(Math.abs(Number(entry.amount)))}
                    </strong>
                    <span className="fee-caption">잔액 {usd(entry.balanceAfter)}</span>
                  </div>
                  <Badge tone={entryTone(entry.entryType)}>{entryLabel(entry.entryType)}</Badge>
                </article>
              );
            })}
          </div>
        </section>
      </section>

      <style>{`
        .credit-note {
          display: flex;
          gap: 10px;
          margin-top: 14px;
          padding: 14px;
          border-radius: 14px;
          background: var(--brand-soft);
          color: var(--brand-dark);
        }
        .credit-note svg {
          flex: 0 0 auto;
          margin-top: 2px;
        }
        .credit-note p {
          margin: 0;
          font-size: 13px;
          font-weight: 600;
          line-height: 1.6;
        }
        .credit-amount-plus {
          color: #15803d;
        }
        .credit-amount-minus {
          color: #b91c1c;
        }
      `}</style>
    </main>
  );
}
