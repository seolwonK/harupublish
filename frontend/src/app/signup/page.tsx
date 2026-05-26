"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "../auth";
import { ApiNotice, AppHeader, Field, TimeZoneSelect } from "../components";

export default function SignupPage() {
  const router = useRouter();
  const { signup } = useAuth();
  const [form, setForm] = useState({
    email: "",
    password: "",
    name: "",
    timeZone: "Asia/Seoul"
  });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await signup(form);
      router.push("/account");
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
          <button className="primary-button wide" disabled={loading}>
            {loading ? "가입 중" : "가입하기"}
          </button>
        </form>
      </section>
    </main>
  );
}
