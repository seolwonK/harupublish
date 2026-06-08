"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { DevCreateTutorResult, DevTestAccount, HaruApiError, haruApi } from "../api";
import { useAuth } from "../auth";
import { AppHeader, ApiNotice, Button, Field } from "../components";

function formatWhen(iso: string) {
  return new Date(iso).toLocaleString("ko-KR", {
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

/** 현재 시각 기준 입장 가능 여부 안내 (입장: 시작 10분 전 ~ 시작 25분 후). */
function joinHint(startIso: string): { label: string; live: boolean } {
  const start = new Date(startIso).getTime();
  const now = Date.now();
  const open = start - 10 * 60 * 1000;
  const end = start + 25 * 60 * 1000;
  if (now >= open && now < end) return { label: "지금 입장 가능", live: true };
  if (now < open) return { label: `${Math.round((open - now) / 60000)}분 후 입장 가능`, live: false };
  return { label: "종료됨", live: false };
}

function errorMessage(err: unknown, fallback: string) {
  if (err instanceof HaruApiError) {
    if (err.status === 404) return "백엔드에서 dev 도구가 비활성화되어 있습니다 (HARU_DEV_TOOLS_ENABLED=true 필요).";
    return err.message;
  }
  return err instanceof Error ? err.message : fallback;
}

function AccountCard({ title, account }: { title: string; account: DevTestAccount }) {
  const { login } = useAuth();
  const router = useRouter();
  const [busy, setBusy] = useState(false);

  async function loginAs() {
    setBusy(true);
    try {
      await login(account.email, account.password);
      router.push(account.activeRole === "TUTOR" ? "/tutor/dashboard" : "/bookings");
    } catch {
      setBusy(false);
    }
  }

  return (
    <div className="dev-account">
      <div>
        <strong>{title}</strong>
        <p>
          <code>{account.email}</code> / <code>{account.password}</code>
        </p>
        <span className="dev-muted">
          #{account.userId} · {account.name} · {account.activeRole}
        </span>
      </div>
      <Button variant="secondary" onClick={() => void loginAs()} disabled={busy}>
        {busy ? "로그인 중…" : "이 계정으로 로그인"}
      </Button>
    </div>
  );
}

export default function DevToolsPage() {
  const [status, setStatus] = useState<"checking" | "enabled" | "disabled">("checking");
  const [count, setCount] = useState("3");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<DevCreateTutorResult | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const checkStatus = useCallback(async () => {
    try {
      await haruApi.devStatus();
      setStatus("enabled");
    } catch {
      setStatus("disabled");
    }
  }, []);

  useEffect(() => {
    void checkStatus();
  }, [checkStatus]);

  async function generate() {
    setMessage(null);
    setError(null);
    setResult(null);
    setBusy(true);
    try {
      const parsed = Number.parseInt(count, 10);
      const lessonCount = Number.isFinite(parsed) && parsed > 0 ? Math.min(parsed, 20) : 3;
      const stamp = new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
      const created = await haruApi.devCreateTutor({
        name: `테스트 튜터 ${stamp}`,
        category: "KOREAN",
        autoLessonCount: lessonCount
      });
      setResult(created);
      setMessage(`신규 튜터 #${created.tutorProfileId} + 테스트 수업 ${created.bookedLessons.length}개 생성 완료`);
    } catch (err) {
      setError(errorMessage(err, "생성에 실패했습니다."));
    } finally {
      setBusy(false);
    }
  }

  const disabled = status === "disabled";

  return (
    <>
      <AppHeader />
      <main className="dev-page">
        <header className="dev-header">
          <h1>테스트 수업 생성</h1>
          <p>버튼 한 번으로 신규 튜터를 만들고, 현재 시각 기준으로 테스트해볼 수업을 생성합니다. 테스트 환경 전용입니다.</p>
        </header>

        {status === "disabled" ? (
          <ApiNotice type="error">
            백엔드에서 dev 도구가 비활성화되어 있습니다. <code>HARU_DEV_TOOLS_ENABLED=true</code>로 백엔드를 실행하세요.
          </ApiNotice>
        ) : null}
        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}

        <section className="dev-panel">
          <div className="dev-controls">
            <Field label="수업 개수">
              <input value={count} onChange={(e) => setCount(e.target.value)} inputMode="numeric" style={{ width: 90 }} />
            </Field>
            <Button variant="primary" onClick={() => void generate()} disabled={disabled || busy}>
              {busy ? "생성 중…" : "테스트 수업 생성"}
            </Button>
          </div>
          <p className="dev-muted">
            첫 수업은 약 5분 뒤로 잡혀 바로 입장 테스트가 가능하고, 나머지는 30분 간격으로 예정됩니다. 수업마다 결제 완료된 예약이 함께 만들어집니다.
          </p>

          {result ? (
            <div className="dev-result">
              <AccountCard title="튜터 계정" account={result.tutorAccount} />
              {result.studentAccount ? <AccountCard title="수업 학생 계정" account={result.studentAccount} /> : null}
              <div className="dev-result-meta">
                <Button as="a" href={`/tutors/${result.tutorProfileId}`} variant="ghost">
                  튜터 프로필 보기 →
                </Button>
              </div>
              <ul className="dev-list">
                {result.bookedLessons.map((lesson) => {
                  const hint = joinHint(lesson.startAt);
                  return (
                    <li key={lesson.bookingId}>
                      예약 #{lesson.bookingId} · {formatWhen(lesson.startAt)}{" "}
                      <span className={hint.live ? "dev-live" : "dev-muted"}>· {hint.label}</span>
                    </li>
                  );
                })}
              </ul>
            </div>
          ) : null}
        </section>
      </main>

      <style>{`
        .dev-page {
          width: min(720px, calc(100vw - 40px));
          margin: 24px auto 60px;
          display: flex;
          flex-direction: column;
          gap: 18px;
        }
        .dev-header h1 { margin: 0 0 6px; font-size: clamp(24px, 4vw, 30px); color: var(--ink); }
        .dev-header p { margin: 0; color: var(--muted); }
        .dev-panel {
          background: var(--panel);
          border: 1px solid var(--line);
          border-radius: 16px;
          box-shadow: var(--shadow-soft);
          padding: clamp(18px, 3vw, 26px);
          display: flex;
          flex-direction: column;
          gap: 14px;
        }
        .dev-controls { display: flex; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
        .dev-muted { color: var(--muted); font-size: 13.5px; margin: 0; }
        .dev-live { color: var(--brand-dark); font-weight: 700; }
        .dev-result {
          margin-top: 6px;
          border-top: 1px dashed var(--line);
          padding-top: 14px;
          display: flex;
          flex-direction: column;
          gap: 10px;
        }
        .dev-account {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 14px;
          flex-wrap: wrap;
          background: var(--brand-soft);
          border-radius: 12px;
          padding: 12px 14px;
        }
        .dev-account code { background: rgba(0,0,0,0.06); padding: 1px 6px; border-radius: 6px; font-size: 13px; }
        .dev-account p { margin: 4px 0; }
        .dev-result-meta { display: flex; gap: 10px; }
        .dev-list { margin: 0; padding-left: 18px; color: var(--ink); font-size: 13.5px; line-height: 1.8; }
      `}</style>
    </>
  );
}
