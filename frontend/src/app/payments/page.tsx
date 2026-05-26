"use client";

import { CheckCircle2, ExternalLink, RefreshCcw, WalletCards } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { haruApi, PaymentResponse, toMoney } from "../api";
import { useAuth } from "../auth";
import { clearPendingBookingIntent, PendingBookingIntent, readPendingBookingIntent } from "../booking-intent";
import { ApiNotice, AppHeader, Badge, Button, EmptyState, SectionHeader, StatCard, statusLabel } from "../components";

function paymentMethodLabel(method: PaymentResponse["paymentMethod"]) {
  const labels: Record<PaymentResponse["paymentMethod"], string> = {
    CARD: "Card",
    PAYPAL: "PayPal",
    SIMPLE_PAY: "Simple Pay",
    LEMON_SQUEEZY: "Lemon Squeezy"
  };
  return labels[method];
}

function statusTone(status: PaymentResponse["status"]) {
  if (status === "PAID") return "green";
  if (status === "FAILED" || status === "CANCELLED") return "red";
  if (status === "REFUND_REQUESTED" || status === "REFUNDED") return "blue";
  return "orange";
}

export default function PaymentsPage() {
  const { accessToken } = useAuth();
  const [payments, setPayments] = useState<PaymentResponse[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendingBookingIntent, setPendingBookingIntent] = useState<PendingBookingIntent | null>(null);
  const [resumeMessage, setResumeMessage] = useState<string | null>(null);
  const [resumeError, setResumeError] = useState<string | null>(null);
  const [createdBookingId, setCreatedBookingId] = useState<number | null>(null);
  const [resumeNonce, setResumeNonce] = useState(0);

  const pendingCount = useMemo(() => payments.filter((payment) => payment.status === "PENDING").length, [payments]);
  const paidCount = useMemo(() => payments.filter((payment) => payment.status === "PAID").length, [payments]);
  const totalRequested = useMemo(
    () => payments.reduce((sum, payment) => sum + Number(payment.totalAmount ?? 0), 0),
    [payments]
  );

  function mergePayment(nextPayment: PaymentResponse) {
    setPayments((current) => {
      const filtered = current.filter((payment) => payment.id !== nextPayment.id);
      return [nextPayment, ...filtered].sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
    });
  }

  function loadPayments() {
    if (!accessToken) return;
    setError(null);
    haruApi
      .getMyPayments(accessToken)
      .then((response) => setPayments(response.payments))
      .catch((err: Error) => setError(err.message));
  }

  useEffect(loadPayments, [accessToken]);

  useEffect(() => {
    setPendingBookingIntent(readPendingBookingIntent());
  }, [accessToken]);

  useEffect(() => {
    if (!accessToken || !pendingBookingIntent) return;

    const token = accessToken;
    const intent = pendingBookingIntent;
    let cancelled = false;

    async function resumeBookingAfterPayment() {
      setResumeError(null);
      setResumeMessage("결제 상태를 확인하는 중입니다. 완료되면 예약을 자동으로 마무리합니다.");

      for (let attempt = 0; attempt < 24; attempt += 1) {
        if (cancelled) return;

        try {
          const payment = await haruApi.getPayment(token, intent.paymentId);
          if (cancelled) return;

          mergePayment(payment);

          if (payment.status === "PAID") {
            setResumeMessage("결제가 확인되었습니다. 예약을 완료하는 중입니다.");

            try {
              const booking = await haruApi.createBooking(token, {
                tutorProfileId: intent.tutorProfileId,
                scheduleSlotId: intent.scheduleSlotId,
                lessonDurationMinutes: intent.lessonDurationMinutes
              });

              if (cancelled) return;

              clearPendingBookingIntent();
              setPendingBookingIntent(null);
              setCreatedBookingId(booking.id);
              setResumeMessage("결제가 확인되어 예약이 완료되었습니다.");
              return;
            } catch (bookingError) {
              if (cancelled) return;

              clearPendingBookingIntent();
              setPendingBookingIntent(null);
              setResumeMessage(null);
              setResumeError(bookingError instanceof Error ? bookingError.message : "결제 후 예약 자동 완료에 실패했습니다.");
              return;
            }
          }

          if (payment.status === "FAILED" || payment.status === "CANCELLED" || payment.status === "REFUNDED") {
            clearPendingBookingIntent();
            setPendingBookingIntent(null);
            setResumeMessage(null);
            setResumeError("결제가 완료되지 않아 예약을 자동으로 만들지 않았습니다.");
            return;
          }
        } catch (resumeFailure) {
          if (cancelled) return;

          if (attempt === 23) {
            setResumeMessage(null);
            setResumeError(resumeFailure instanceof Error ? resumeFailure.message : "결제 상태 확인에 실패했습니다.");
            return;
          }
        }

        await new Promise((resolve) => window.setTimeout(resolve, 2500));
      }

      if (!cancelled) {
        setResumeMessage("결제 확인이 지연되고 있습니다. 아래 결제 내역에서 상태를 다시 확인해 주세요.");
      }
    }

    void resumeBookingAfterPayment();

    return () => {
      cancelled = true;
    };
  }, [accessToken, pendingBookingIntent, resumeNonce]);

  async function requestRefund(paymentId: number) {
    if (!accessToken) return;
    setError(null);
    setMessage(null);
    try {
      await haruApi.requestRefund(accessToken, paymentId, "프론트에서 환불 요청");
      setMessage("환불 요청이 접수되었습니다.");
      loadPayments();
    } catch (err) {
      setError(err instanceof Error ? err.message : "환불 요청에 실패했습니다.");
    }
  }

  return (
    <main className="page-shell compact">
      <AppHeader />

      <section className="payments-layout">
        <aside className="summary-rail">
          <SectionHeader eyebrow="Payments" title="결제 요약" />
          <StatCard label="결제 요청" value={String(payments.length)} hint="내 결제 건수" />
          <StatCard label="결제 대기" value={String(pendingCount)} hint="Lemon Squeezy 완료 대기" />
          <StatCard label="결제 완료" value={String(paidCount)} hint="웹훅 동기화 완료" />
          <StatCard label="요청 총액" value={toMoney(totalRequested)} hint="USD 기준" />
        </aside>

        <section className="panel payments-panel">
          <SectionHeader
            eyebrow="Lemon Squeezy"
            title="결제 내역"
            description="튜터별 회차권 결제 요청, Lemon Squeezy checkout 링크, 환불 요청 상태를 확인합니다."
            action={<Button as="a" href="/#experts" variant="secondary">튜터 찾기</Button>}
          />

          {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
          {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
          {resumeMessage ? <ApiNotice type={createdBookingId ? "success" : "info"}>{resumeMessage}</ApiNotice> : null}
          {resumeError ? <ApiNotice type="error">{resumeError}</ApiNotice> : null}
          {pendingBookingIntent && !createdBookingId ? (
            <div className="button-row">
              <Button variant="secondary" onClick={() => setResumeNonce((value) => value + 1)}>
                <RefreshCcw size={14} /> 결제 상태 다시 확인
              </Button>
            </div>
          ) : null}
          {createdBookingId ? (
            <div className="button-row">
              <Button as="a" href="/bookings">
                <CheckCircle2 size={16} /> 내 예약 보기
              </Button>
            </div>
          ) : null}
          {!accessToken ? (
            <div className="booking-empty-panel">
              <EmptyState title="로그인이 필요합니다" body="결제 내역을 보려면 먼저 로그인해주세요." />
              <Button as="a" href="/login">
                로그인하기
              </Button>
            </div>
          ) : null}
          {accessToken && payments.length === 0 ? (
            <EmptyState title="결제 요청이 없습니다" body="튜터 상세 화면에서 회차권을 선택하고 Lemon Squeezy 결제를 시작하세요." />
          ) : null}

          <div className="payment-list">
            {payments.map((payment) => (
              <article className="payment-card" key={payment.id}>
                <div className="payment-icon">
                  <WalletCards size={22} />
                </div>
                <div>
                  <strong>튜터 #{payment.tutorProfileId}</strong>
                  <p>
                    {payment.lessonDurationMinutes}분 · {payment.lessonPackCount}회 · {paymentMethodLabel(payment.paymentMethod)}
                  </p>
                  <span>{new Date(payment.createdAt).toLocaleString("ko-KR")}</span>
                  {payment.providerOrderIdentifier ? <span>주문번호 {payment.providerOrderIdentifier}</span> : null}
                </div>
                <div className="payment-amount">
                  <strong>{toMoney(payment.totalAmount)}</strong>
                  <span>수수료 {toMoney(payment.studentFeeAmount)}</span>
                </div>
                <Badge tone={statusTone(payment.status)}>{statusLabel(payment.status)}</Badge>
                <div className="payment-actions">
                  {payment.status === "PENDING" && payment.checkoutUrl ? (
                    <Button as="a" href={payment.checkoutUrl} variant="secondary">
                      <ExternalLink size={14} /> 결제 계속
                    </Button>
                  ) : null}
                  <Button variant="ghost" onClick={() => void requestRefund(payment.id)} disabled={payment.status !== "PAID"}>
                    <RefreshCcw size={14} />
                    환불 요청
                  </Button>
                </div>
              </article>
            ))}
          </div>
        </section>
      </section>
    </main>
  );
}
