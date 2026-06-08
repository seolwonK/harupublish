"use client";

import { Check, RefreshCcw, Wallet } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { formatMoney, haruApi, type RefundRequestResponse } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, Badge, Button, EmptyState, Sidebar } from "../../components";

// 환불/크레딧 금액은 USD 액면 고정 표기 (Haru Credits = 환불 전용 USD).
function usd(value: number | string | null | undefined) {
  return formatMoney(value, { currency: "USD" });
}

export default function AdminRefundsPage() {
  const { accessToken } = useAuth();
  const [requests, setRequests] = useState<RefundRequestResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError(null);
    try {
      setRequests(await haruApi.getRefundRequests(accessToken));
    } catch (err) {
      setError(err instanceof Error ? err.message : "환불 요청 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    void load();
  }, [load]);

  async function approve(paymentId: number) {
    if (!accessToken) {
      setError("관리자 계정으로 로그인해주세요.");
      return;
    }

    setBusyId(paymentId);
    setMessage(null);
    setError(null);
    try {
      const payment = await haruApi.approveRefund(accessToken, paymentId);
      // 미사용 회차 * 회차당 USD 단가가 학생의 Haru Credits로 발급됨.
      setMessage(`결제 #${payment.id} 환불 승인 완료 · Haru Credits ${usd(payment.totalAmount)} 발급 (상태: ${payment.status}).`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "환불 승인 처리에 실패했습니다.");
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
            <Badge tone="blue">Refund Queue</Badge>
            <h1>환불 승인</h1>
            <p>학생의 환불 요청을 검토하고 승인합니다. 승인 시 미사용 회차가 Haru Credits(USD)로 발급되고 결제가 환불 완료로 전환됩니다.</p>
          </div>
          <Button variant="ghost" onClick={() => void load()}>
            <RefreshCcw size={16} /> 새로고침
          </Button>
        </header>

        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        {!accessToken ? (
          <EmptyState title="관리자 로그인이 필요합니다" body="ADMIN 계정으로 로그인하면 환불 요청을 승인할 수 있습니다." />
        ) : null}

        <section className="panel">
          {loading ? <EmptyState title="불러오는 중" body="환불 요청 대기 목록을 조회하고 있습니다." /> : null}
          {!loading && accessToken && requests.length === 0 ? (
            <EmptyState title="환불 요청이 없습니다" body="학생이 환불을 요청하면 이곳에 표시됩니다." />
          ) : null}
          {requests.length > 0 ? (
            <div className="payment-list">
              {requests.map((request) => (
                <article className="payment-card" key={request.id}>
                  <div className="payment-icon">
                    <Wallet size={22} />
                  </div>
                  <div>
                    <strong>결제 #{request.id} · 학생 #{request.studentUserId} · 튜터 #{request.tutorProfileId}</strong>
                    <p>
                      환불 요청액 {usd(request.totalAmount)}
                      <span className="usd-fixed-note"> · 승인 시 Haru Credits(USD)로 발급</span>
                    </p>
                    <span>요청 사유: {request.refundReason?.trim() || "사유 없음"}</span>
                    <span>{new Date(request.createdAt).toLocaleString("ko-KR")}</span>
                  </div>
                  <Badge tone="orange">환불 요청</Badge>
                  <div className="payment-actions">
                    <Button onClick={() => void approve(request.id)} disabled={busyId === request.id}>
                      <Check size={14} /> {busyId === request.id ? "처리 중..." : "환불 승인"}
                    </Button>
                  </div>
                </article>
              ))}
            </div>
          ) : null}
        </section>
      </section>
    </main>
  );
}
