"use client";

import { Save, SlidersHorizontal } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { haruApi, type PlatformSettingsResponse, type UpdatePlatformSettingsRequest } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, Badge, Button, EmptyState, Field, SectionHeader, Sidebar } from "../../components";

// 화면에서는 퍼센트(10)로 입력받고, 계약(0.10)으로 변환해 전송한다.
type FormState = {
  studentFeePercent: string;
  platformFeePercent: string;
  fivePackDiscountPercent: string;
  tenPackDiscountPercent: string;
  withdrawalFeePaypalPercent: string;
  withdrawalFeePayoneerPercent: string;
  withdrawalFeeDomesticPercent: string;
  promoWithdrawalFeePercent: string;
  cancellationCutoffHours: string;
  creditExpiryMonths: string;
};

function toPercentInput(rate: number | string | null | undefined) {
  const value = Number(rate ?? 0);
  return Number.isFinite(value) ? String(Math.round(value * 100 * 100) / 100) : "0";
}

function fromSettings(settings: PlatformSettingsResponse): FormState {
  return {
    studentFeePercent: toPercentInput(settings.studentFeeRate),
    platformFeePercent: toPercentInput(settings.platformFeeRate),
    fivePackDiscountPercent: toPercentInput(settings.fivePackDiscountRate),
    tenPackDiscountPercent: toPercentInput(settings.tenPackDiscountRate),
    withdrawalFeePaypalPercent: toPercentInput(settings.withdrawalFeeRatePaypal),
    withdrawalFeePayoneerPercent: toPercentInput(settings.withdrawalFeeRatePayoneer),
    withdrawalFeeDomesticPercent: toPercentInput(settings.withdrawalFeeRateDomestic),
    promoWithdrawalFeePercent: toPercentInput(settings.promoWithdrawalFeeRate),
    cancellationCutoffHours: String(settings.cancellationCutoffHours ?? 3),
    creditExpiryMonths: String(settings.creditExpiryMonths ?? 12)
  };
}

function toRate(percent: string) {
  const value = Number(percent);
  return Number.isFinite(value) ? Math.round((value / 100) * 10000) / 10000 : 0;
}

const DEFAULT_FORM: FormState = {
  studentFeePercent: "10",
  platformFeePercent: "15",
  fivePackDiscountPercent: "5",
  tenPackDiscountPercent: "10",
  withdrawalFeePaypalPercent: "3",
  withdrawalFeePayoneerPercent: "3",
  withdrawalFeeDomesticPercent: "5",
  promoWithdrawalFeePercent: "5",
  cancellationCutoffHours: "3",
  creditExpiryMonths: "12"
};

export default function AdminSettingsPage() {
  const { accessToken } = useAuth();
  const [form, setForm] = useState<FormState>(DEFAULT_FORM);
  const [loaded, setLoaded] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    if (!accessToken) return;
    setError(null);
    try {
      const settings = await haruApi.getPlatformSettings(accessToken);
      setForm(fromSettings(settings));
      setLoaded(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "수수료 설정을 불러오지 못했습니다.");
    }
  }, [accessToken]);

  useEffect(() => {
    void load();
  }, [load]);

  function update(key: keyof FormState, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function save() {
    if (!accessToken) {
      setError("관리자 계정으로 로그인해주세요.");
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    const body: UpdatePlatformSettingsRequest = {
      studentFeeRate: toRate(form.studentFeePercent),
      platformFeeRate: toRate(form.platformFeePercent),
      fivePackDiscountRate: toRate(form.fivePackDiscountPercent),
      tenPackDiscountRate: toRate(form.tenPackDiscountPercent),
      withdrawalFeeRatePaypal: toRate(form.withdrawalFeePaypalPercent),
      withdrawalFeeRatePayoneer: toRate(form.withdrawalFeePayoneerPercent),
      withdrawalFeeRateDomestic: toRate(form.withdrawalFeeDomesticPercent),
      promoWithdrawalFeeRate: toRate(form.promoWithdrawalFeePercent),
      cancellationCutoffHours: Number(form.cancellationCutoffHours) || 3,
      creditExpiryMonths: Number(form.creditExpiryMonths) || 12
    };
    try {
      const settings = await haruApi.updatePlatformSettings(accessToken, body);
      setForm(fromSettings(settings));
      setMessage("수수료/정책 설정이 저장되었습니다. 이후 신규 결제부터 적용됩니다 (기존 스냅샷 소급 없음).");
    } catch (err) {
      setError(err instanceof Error ? err.message : "설정 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="dashboard-layout">
      <Sidebar admin />
      <section className="dashboard-main">
        <header className="admin-title">
          <div>
            <Badge tone="blue">Settings</Badge>
            <h1>수수료 / 정책 설정</h1>
            <p>학생 마크업·플랫폼 수수료·회차 할인·인출 수수료·취소 정책을 런타임으로 조정합니다. 변경은 신규 결제부터 적용되며 기존 결제 스냅샷에는 소급되지 않습니다.</p>
          </div>
          <span className="usd-fixed-note">진실 원장 USD</span>
        </header>

        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        {!accessToken ? <EmptyState title="관리자 로그인이 필요합니다" body="ADMIN 계정으로 로그인하면 수수료 설정을 조정할 수 있습니다." /> : null}

        <section className="panel">
          <SectionHeader eyebrow="Fees" title="수수료 요율 (%)" description="학생은 표시가에 포함, 튜터는 수익에서 차감됩니다." />
          <div className="form-grid">
            <Field label="학생 마크업 (%)" hint="결제 표시가에 포함되는 플랫폼 마크업">
              <input value={form.studentFeePercent} onChange={(event) => update("studentFeePercent", event.target.value)} inputMode="decimal" />
            </Field>
            <Field label="플랫폼 수수료 (%)" hint="튜터 gross에서 차감 (순수익 = 1 - 요율)">
              <input value={form.platformFeePercent} onChange={(event) => update("platformFeePercent", event.target.value)} inputMode="decimal" />
            </Field>
            <Field label="5회권 할인 (%)">
              <input value={form.fivePackDiscountPercent} onChange={(event) => update("fivePackDiscountPercent", event.target.value)} inputMode="decimal" />
            </Field>
            <Field label="10회권 할인 (%)">
              <input value={form.tenPackDiscountPercent} onChange={(event) => update("tenPackDiscountPercent", event.target.value)} inputMode="decimal" />
            </Field>
          </div>
        </section>

        <section className="panel">
          <SectionHeader eyebrow="Withdrawal" title="인출 수수료 (%)" />
          <div className="form-grid">
            <Field label="PayPal (%)">
              <input value={form.withdrawalFeePaypalPercent} onChange={(event) => update("withdrawalFeePaypalPercent", event.target.value)} inputMode="decimal" />
            </Field>
            <Field label="Payoneer (%)">
              <input value={form.withdrawalFeePayoneerPercent} onChange={(event) => update("withdrawalFeePayoneerPercent", event.target.value)} inputMode="decimal" />
            </Field>
            <Field label="국내 계좌 (%)">
              <input value={form.withdrawalFeeDomesticPercent} onChange={(event) => update("withdrawalFeeDomesticPercent", event.target.value)} inputMode="decimal" />
            </Field>
            <Field label="프로모 면제 강사 인출 (%)" hint="수수료 면제 강사도 차감되는 고정 인출 수수료">
              <input value={form.promoWithdrawalFeePercent} onChange={(event) => update("promoWithdrawalFeePercent", event.target.value)} inputMode="decimal" />
            </Field>
          </div>
        </section>

        <section className="panel">
          <SectionHeader eyebrow="Policy" title="정책" />
          <div className="form-grid">
            <Field label="취소 가능 시간 (수업 전, 시간)" hint="이 시간 이내·노쇼는 환불 0">
              <input value={form.cancellationCutoffHours} onChange={(event) => update("cancellationCutoffHours", event.target.value)} inputMode="numeric" />
            </Field>
            <Field label="크레딧 만료 (개월, 계정 비활성 기준)">
              <input value={form.creditExpiryMonths} onChange={(event) => update("creditExpiryMonths", event.target.value)} inputMode="numeric" />
            </Field>
          </div>
          <div className="button-row">
            <Button onClick={() => void save()} disabled={saving || !accessToken}>
              <Save size={16} /> {saving ? "저장 중..." : "설정 저장"}
            </Button>
            {loaded ? (
              <Button variant="ghost" onClick={() => void load()}>
                <SlidersHorizontal size={16} /> 되돌리기
              </Button>
            ) : null}
          </div>
        </section>
      </section>
    </main>
  );
}
