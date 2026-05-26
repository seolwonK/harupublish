"use client";

import { useEffect, useState } from "react";
import { haruApi, Role } from "../api";
import { useAuth } from "../auth";
import { ApiNotice, AppHeader, EmptyState, Field, RoleModeSwitcher, TimeZoneSelect, statusLabel } from "../components";

export default function AccountPage() {
  const { user, accessToken, refreshMe } = useAuth();
  const [form, setForm] = useState({ name: "", mobileNumber: "", timeZone: "Asia/Seoul" });
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user) {
      setForm({
        name: user.name,
        mobileNumber: user.mobileNumber ?? "",
        timeZone: user.timeZone
      });
    }
  }, [user]);

  async function saveProfile(event: React.FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setError(null);
    setMessage(null);
    try {
      await haruApi.updateMe(accessToken, form);
      await refreshMe();
      setMessage("내 프로필이 저장되었습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "저장에 실패했습니다.");
    }
  }

  async function changeRole(role: Role) {
    if (!accessToken) return;
    setError(null);
    setMessage(null);
    try {
      await haruApi.changeActiveRole(accessToken, role);
      await refreshMe();
      setMessage(`${role} 모드로 변경했습니다.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "모드 변경에 실패했습니다.");
    }
  }

  if (!user) {
    return (
      <main className="page-shell">
        <AppHeader />
        <EmptyState title="로그인이 필요합니다" body="내 계정 정보를 관리하려면 먼저 로그인해주세요." />
      </main>
    );
  }

  return (
    <main className="page-shell">
      <AppHeader />
      <section className="account-grid">
        <div className="panel auth-panel">
          <h1>내 계정</h1>
          <p>{user.email}</p>
          {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
          {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
          <form className="form-grid" onSubmit={saveProfile}>
            <Field label="이름">
              <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            </Field>
            <Field label="휴대폰 번호">
              <input value={form.mobileNumber} onChange={(event) => setForm({ ...form, mobileNumber: event.target.value })} />
            </Field>
            <Field label="타임존" hint="예약과 스케줄 시간을 이 기준으로 보여줍니다.">
              <TimeZoneSelect value={form.timeZone} onChange={(timeZone) => setForm({ ...form, timeZone })} />
            </Field>
            <button className="primary-button wide">저장</button>
          </form>
        </div>
        <div className="panel auth-panel">
          <h2>역할과 튜터 상태</h2>
          <div className="info-list">
            <p><strong>현재 모드</strong><span>{user.activeRole}</span></p>
            <p><strong>보유 역할</strong><span>{user.roles.join(", ")}</span></p>
            <p><strong>튜터 프로필</strong><span>{statusLabel(user.tutorProfileStatus)}</span></p>
          </div>
          <div className="button-row">
            <RoleModeSwitcher />
            {user.roles.map((role) => (
              <button className="ghost-button" key={role} onClick={() => void changeRole(role)}>
                {role}
              </button>
            ))}
          </div>
          <a className="primary-button wide" href="/tutor/profile">레슨 프로필 관리</a>
        </div>
      </section>
    </main>
  );
}
