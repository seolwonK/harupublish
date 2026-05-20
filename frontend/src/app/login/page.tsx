"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ApiNotice, AppHeader, Field } from "../components";
import { useAuth } from "../auth";

export default function LoginPage() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState("admin@admin.com");
  const [password, setPassword] = useState("admin1234");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await login(email, password);
      router.push("/account");
    } catch (err) {
      setError(err instanceof Error ? err.message : "로그인에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="page-shell">
      <AppHeader />
      <section className="auth-panel panel">
        <h1>로그인</h1>
        <p>백엔드 JWT 인증 API와 연결됩니다.</p>
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        <form className="form-grid" onSubmit={submit}>
          <Field label="이메일">
            <input value={email} onChange={(event) => setEmail(event.target.value)} />
          </Field>
          <Field label="비밀번호">
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </Field>
          <button className="primary-button wide" disabled={loading}>
            {loading ? "로그인 중" : "로그인"}
          </button>
        </form>
        <a className="link-button" href="/signup">계정 만들기</a>
      </section>
    </main>
  );
}
