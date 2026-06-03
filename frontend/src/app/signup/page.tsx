"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "../auth";
import { ApiNotice, AppHeader, Field, TimeZoneSelect } from "../components";

function safeRedirectTarget(value: string | null, fallback = "/account") {
  if (!value?.startsWith("/")) return fallback;
  return value;
}

function SignupPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { signup } = useAuth();
  const [form, setForm] = useState({
    email: "",
    password: "",
    name: "",
    timeZone: "Asia/Seoul"
  });
  const [agreed, setAgreed] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const redirectTarget = safeRedirectTarget(searchParams.get("redirect"));

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    if (!agreed) {
      setError("이용약관 및 개인정보처리방침에 동의해야 가입할 수 있습니다.");
      return;
    }
    setLoading(true);
    try {
      await signup(form);
      router.push(redirectTarget);
    } catch (err) {
      setError(err instanceof Error ? err.message : "회원가입에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="page-shell">
      <AppHeader />
      <section className="auth-panel panel">
        <h1>회원가입</h1>
        <p>가입 직후 학생 모드로 시작하고, 예약 시간은 선택한 타임존 기준으로 보여줍니다.</p>
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        <form className="form-grid" onSubmit={submit}>
          <Field label="이메일" hint="로그인에 사용할 이메일입니다.">
            <input type="email" autoComplete="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
          </Field>
          <Field label="비밀번호" hint="8자 이상 입력해주세요.">
            <input type="password" autoComplete="new-password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />
          </Field>
          <Field label="이름">
            <input autoComplete="name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
          </Field>
          <Field label="타임존" hint="수업 시간 표시와 스케줄 선택에 사용합니다.">
            <TimeZoneSelect value={form.timeZone} onChange={(timeZone) => setForm({ ...form, timeZone })} />
          </Field>
          <label className="consent-row">
            <input
              type="checkbox"
              checked={agreed}
              onChange={(event) => setAgreed(event.target.checked)}
              aria-required="true"
            />
            <span>
              <Link href="/terms" target="_blank" rel="noopener noreferrer">
                이용약관
              </Link>{" "}
              및{" "}
              <Link href="/privacy" target="_blank" rel="noopener noreferrer">
                개인정보처리방침
              </Link>
              에 동의합니다. <em>(필수)</em>
            </span>
          </label>
          <button className="primary-button wide" disabled={loading || !agreed}>
            {loading ? "가입 중" : "가입하기"}
          </button>
        </form>
      </section>

      <style>{`
        .consent-row {
          display: flex;
          align-items: flex-start;
          gap: 10px;
          margin-top: 4px;
          font-size: 14px;
          line-height: 1.6;
          color: var(--ink);
          cursor: pointer;
        }
        .consent-row input[type="checkbox"] {
          width: 18px;
          height: 18px;
          margin-top: 2px;
          flex-shrink: 0;
          accent-color: var(--brand);
          cursor: pointer;
        }
        .consent-row a {
          color: var(--brand-dark);
          font-weight: 700;
          text-decoration: underline;
        }
        .consent-row em {
          font-style: normal;
          color: var(--brand);
          font-weight: 700;
        }
      `}</style>
    </main>
  );
}

export default function SignupPage() {
  return (
    <Suspense fallback={null}>
      <SignupPageContent />
    </Suspense>
  );
}
