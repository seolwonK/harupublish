"use client";

import { useCallback, useEffect, useState } from "react";
import { haruApi, TutorProfileResponse } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, EmptyState, Field, Sidebar, categoryLabel, statusLabel } from "../../components";

export default function AdminTutorsPage() {
  const { accessToken, user } = useAuth();
  const [profiles, setProfiles] = useState<TutorProfileResponse[]>([]);
  const [manualId, setManualId] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const loadPendingProfiles = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError(null);
    try {
      setProfiles(await haruApi.getPendingTutors(accessToken));
    } catch (err) {
      setError(err instanceof Error ? err.message : "승인 대기 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    void loadPendingProfiles();
  }, [loadPendingProfiles]);

  async function decide(profileId: number, action: "approve" | "reject") {
    if (!accessToken) {
      setError("관리자 계정으로 로그인해주세요.");
      return;
    }

    setMessage(null);
    setError(null);
    try {
      const profile =
        action === "approve"
          ? await haruApi.approveTutor(accessToken, profileId)
          : await haruApi.rejectTutor(accessToken, profileId);
      setMessage(`${profile.displayName ?? `프로필 #${profile.id}`} 처리 완료: ${statusLabel(profile.status)}`);
      await loadPendingProfiles();
    } catch (err) {
      setError(err instanceof Error ? err.message : "튜터 승인 처리에 실패했습니다.");
    }
  }

  async function decideManual(action: "approve" | "reject") {
    const profileId = Number(manualId);
    if (!Number.isInteger(profileId) || profileId <= 0) {
      setError("튜터 프로필 ID는 1 이상의 숫자로 입력해주세요.");
      return;
    }
    await decide(profileId, action);
  }

  return (
    <main className="dashboard-layout">
      <Sidebar admin />
      <section className="dashboard-main">
        <header className="admin-title">
          <div>
            <h1>튜터 승인 관리</h1>
            <p>승인 대기 프로필을 검토하고 공개 튜터로 전환합니다.</p>
          </div>
          <button>{user?.activeRole ?? "로그인 필요"}</button>
        </header>

        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}

        {!accessToken ? (
          <EmptyState title="관리자 로그인이 필요합니다" body="admin@admin.com 계정으로 로그인한 뒤 승인 대기 목록을 확인하세요." />
        ) : null}

        <section className="panel approval-toolbar">
          <div>
            <h2>수동 처리</h2>
            <p>목록에 없거나 직접 확인한 프로필 ID를 입력해 승인/반려할 수 있습니다.</p>
          </div>
          <Field label="튜터 프로필 ID">
            <input value={manualId} onChange={(event) => setManualId(event.target.value)} placeholder="예: 12" inputMode="numeric" />
          </Field>
          <div className="button-row">
            <button className="primary-button" onClick={() => void decideManual("approve")}>
              승인
            </button>
            <button className="ghost-button" onClick={() => void decideManual("reject")}>
              반려
            </button>
          </div>
        </section>

        <section className="panel approval-list">
          <div className="section-title-row">
            <h2>승인 대기 목록</h2>
            <button className="ghost-button" onClick={() => void loadPendingProfiles()}>
              새로고침
            </button>
          </div>
          {loading ? <EmptyState title="불러오는 중" body="PENDING 상태의 튜터 프로필을 조회하고 있습니다." /> : null}
          {!loading && accessToken && profiles.length === 0 ? (
            <EmptyState title="승인 대기 프로필이 없습니다" body="튜터가 프로필을 작성하고 승인 요청을 제출하면 이곳에 표시됩니다." />
          ) : null}
          {profiles.map((profile) => (
            <article className="approval-card" key={profile.id}>
              <div>
                <span className="tag">{statusLabel(profile.status)}</span>
                <h3>{profile.displayName || `튜터 프로필 #${profile.id}`}</h3>
                <p>{profile.shortIntroduction || "짧은 소개가 아직 없습니다."}</p>
                <div className="approval-meta">
                  <span>ID {profile.id}</span>
                  <span>{categoryLabel(profile.category)}</span>
                  <span>{profile.availableLanguages?.join(", ") || "언어 미입력"}</span>
                  <span>25분 USD {profile.lessonPrice25Amount ?? "-"}</span>
                  <span>제출 {profile.submittedAt ? new Date(profile.submittedAt).toLocaleString("ko-KR") : "-"}</span>
                </div>
              </div>
              <div className="approval-actions">
                <button className="primary-button" onClick={() => void decide(profile.id, "approve")}>
                  승인
                </button>
                <button className="ghost-button" onClick={() => void decide(profile.id, "reject")}>
                  반려
                </button>
              </div>
            </article>
          ))}
        </section>
      </section>
    </main>
  );
}
