"use client";

import { CheckCircle2, Circle, ImagePlus, Link2, Send, UserRound } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { haruApi, isYoutubeUrl, resolveAssetUrl, TutorCategory, TutorProfileRequest, TutorProfileResponse, youtubeEmbedUrl } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, BackButton, Badge, EmptyState, Field, Sidebar, statusLabel } from "../../components";

type ProfileErrors = Partial<Record<keyof TutorProfileRequest, string>>;
type ValidationMode = "save" | "submit";

const requiredSubmitFields: Array<keyof TutorProfileRequest> = [
  "displayName",
  "shortIntroduction",
  "aboutMe",
  "whatIOffer",
  "availableLanguages",
  "lessonPrice25Amount",
  "lessonPrice50Amount"
];

const emptyProfile: TutorProfileRequest = {
  displayName: "",
  shortIntroduction: "",
  aboutMe: "",
  whatIOffer: "한국어 회화, TOPIK 준비, 발음 교정",
  category: "KOREAN",
  profileImageUrl: "",
  introVideoUrl: "",
  thumbnailUrl: "",
  availableLanguages: ["한국어", "영어"],
  lessonPrice25Amount: 13,
  lessonPrice50Amount: 25,
  availableTimeNote: "",
  paymentMethod: ""
};

const categoryOptions: Array<[TutorCategory, string]> = [
  ["KOREAN", "한국어"],
  ["KPOP", "K-POP"],
  ["KBEAUTY", "K-뷰티"],
  ["OTHER", "기타"]
];

function isOptionalUrl(value: string) {
  if (!value.trim()) return true;
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function validateProfile(form: TutorProfileRequest, mode: ValidationMode) {
  const errors: ProfileErrors = {};

  if (mode === "submit" && !form.displayName.trim()) errors.displayName = "학생에게 보일 튜터 이름을 입력해주세요.";
  if (form.displayName.length > 100) errors.displayName = "표시 이름은 100자 이하로 입력해주세요.";
  if (mode === "submit" && !form.shortIntroduction.trim()) errors.shortIntroduction = "튜터 카드에 보일 짧은 소개를 입력해주세요.";
  if (form.shortIntroduction.length > 255) errors.shortIntroduction = "짧은 소개는 255자 이하로 입력해주세요.";
  if (mode === "submit" && !form.aboutMe.trim()) errors.aboutMe = "자기소개를 입력해주세요.";
  if (mode === "submit" && !form.whatIOffer.trim()) errors.whatIOffer = "제공하는 수업 내용을 입력해주세요.";
  if (mode === "submit" && form.availableLanguages.length === 0) errors.availableLanguages = "가능 언어를 1개 이상 입력해주세요.";
  if (!form.lessonPrice25Amount || form.lessonPrice25Amount < 1) errors.lessonPrice25Amount = "25분 수업 가격은 1 이상이어야 합니다.";
  if (!form.lessonPrice50Amount || form.lessonPrice50Amount < 1) errors.lessonPrice50Amount = "50분 수업 가격은 1 이상이어야 합니다.";
  if (form.lessonPrice25Amount > 0 && form.lessonPrice50Amount > 0 && form.lessonPrice50Amount < form.lessonPrice25Amount) {
    errors.lessonPrice50Amount = "50분 가격은 25분 가격보다 작을 수 없습니다.";
  }
  if (!isOptionalUrl(form.profileImageUrl)) errors.profileImageUrl = "이미지 주소는 http 또는 https로 시작해야 합니다.";
  if (!isOptionalUrl(form.thumbnailUrl)) errors.thumbnailUrl = "썸네일 주소는 http 또는 https로 시작해야 합니다.";
  if (!isYoutubeUrl(form.introVideoUrl)) errors.introVideoUrl = "소개 영상은 YouTube 링크만 미리보기로 지원합니다.";

  return errors;
}

function focusFirstError(errors: ProfileErrors) {
  const firstField = requiredSubmitFields.find((field) => errors[field]) ?? (Object.keys(errors)[0] as keyof TutorProfileRequest | undefined);
  if (!firstField) return;

  const target = document.querySelector<HTMLElement>(`[name="${firstField}"]`);
  target?.scrollIntoView({ behavior: "smooth", block: "center" });
  target?.focus({ preventScroll: true });
}

function profileRequestFromResponse(profile: TutorProfileResponse): TutorProfileRequest {
  return {
    displayName: profile.displayName ?? "",
    shortIntroduction: profile.shortIntroduction ?? "",
    aboutMe: profile.aboutMe ?? "",
    whatIOffer: profile.whatIOffer ?? emptyProfile.whatIOffer,
    category: profile.category ?? "KOREAN",
    profileImageUrl: profile.profileImageUrl ?? "",
    introVideoUrl: profile.introVideoUrl ?? "",
    thumbnailUrl: profile.thumbnailUrl ?? "",
    availableLanguages: profile.availableLanguages?.length ? profile.availableLanguages : emptyProfile.availableLanguages,
    lessonPrice25Amount: Number(profile.lessonPrice25Amount ?? emptyProfile.lessonPrice25Amount),
    lessonPrice50Amount: Number(profile.lessonPrice50Amount ?? emptyProfile.lessonPrice50Amount),
    availableTimeNote: profile.availableTimeNote ?? "",
    paymentMethod: profile.paymentMethod ?? ""
  };
}

function completedRequiredCount(form: TutorProfileRequest) {
  return [
    form.displayName.trim(),
    form.shortIntroduction.trim(),
    form.aboutMe.trim(),
    form.whatIOffer.trim(),
    form.availableLanguages.length > 0,
    form.lessonPrice25Amount > 0,
    form.lessonPrice50Amount > 0
  ].filter(Boolean).length;
}

export default function TutorProfileManagePage() {
  const { user, accessToken, refreshMe } = useAuth();
  const [profile, setProfile] = useState<TutorProfileResponse | null>(null);
  const [form, setForm] = useState<TutorProfileRequest>(emptyProfile);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [validationMode, setValidationMode] = useState<ValidationMode>("save");
  const [saving, setSaving] = useState(false);
  const [uploadingImageField, setUploadingImageField] = useState<"profileImageUrl" | "thumbnailUrl" | null>(null);

  const saveErrors = useMemo(() => validateProfile(form, "save"), [form]);
  const visibleErrors = submitAttempted ? validateProfile(form, validationMode) : saveErrors;
  const introVideoEmbedUrl = youtubeEmbedUrl(form.introVideoUrl);
  const completion = completedRequiredCount(form);
  const canSubmit = completion === 7 && Object.keys(validateProfile(form, "submit")).length === 0;

  useEffect(() => {
    if (!accessToken) return;
    haruApi
      .getMyTutorProfile(accessToken)
      .then((nextProfile) => {
        setProfile(nextProfile);
        setForm(profileRequestFromResponse(nextProfile));
      })
      .catch(() => undefined);
  }, [accessToken]);

  async function switchTutor() {
    if (!accessToken) return;
    setError(null);
    setMessage(null);
    setSaving(true);
    try {
      const nextProfile = await haruApi.switchToTutor(accessToken);
      setProfile(nextProfile);
      setForm(profileRequestFromResponse(nextProfile));
      await refreshMe();
      setMessage("튜터 센터가 활성화되었습니다. 이제 공개 프로필을 작성할 수 있습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "튜터 전환에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function save(event?: React.FormEvent) {
    event?.preventDefault();
    if (!accessToken) return false;
    setValidationMode("save");
    setSubmitAttempted(true);
    setError(null);
    setMessage(null);
    if (Object.keys(saveErrors).length > 0) {
      focusFirstError(saveErrors);
      setError("입력값을 확인해주세요. 이미지와 영상은 비워둘 수 있지만, 입력한 URL은 올바른 형식이어야 합니다.");
      return false;
    }
    setSaving(true);
    try {
      const nextProfile = await haruApi.updateMyTutorProfile(accessToken, form);
      setProfile(nextProfile);
      await refreshMe();
      setMessage("프로필을 임시저장했습니다.");
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로필 저장에 실패했습니다.");
      return false;
    } finally {
      setSaving(false);
    }
  }

  async function uploadImage(field: "profileImageUrl" | "thumbnailUrl", file: File | null) {
    if (!accessToken || !file) return;
    setError(null);
    setMessage(null);
    setUploadingImageField(field);
    try {
      const { url } = await haruApi.uploadTutorProfileImage(accessToken, file);
      const nextForm = { ...form, [field]: url };
      setForm(nextForm);
      const nextProfile = await haruApi.updateMyTutorProfile(accessToken, nextForm);
      setProfile(nextProfile);
      await refreshMe();
      setMessage(field === "profileImageUrl" ? "프로필 이미지를 저장했습니다." : "썸네일 이미지를 저장했습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "이미지 업로드 또는 저장에 실패했습니다.");
    } finally {
      setUploadingImageField(null);
    }
  }

  async function submitProfile() {
    if (!accessToken) return;
    setValidationMode("submit");
    setSubmitAttempted(true);
    setError(null);
    setMessage(null);
    const submitErrors = validateProfile(form, "submit");
    if (Object.keys(submitErrors).length > 0) {
      focusFirstError(submitErrors);
      setError("승인 요청 전에 필수 항목을 완성해주세요. 이미지, 썸네일, 영상, 정산 수단은 나중에 추가해도 됩니다.");
      return;
    }
    const saved = await save();
    if (!saved) return;
    try {
      const nextProfile = await haruApi.submitMyTutorProfile(accessToken);
      setProfile(nextProfile);
      await refreshMe();
      setMessage("승인 요청을 제출했습니다. 관리자가 검토하면 공개 튜터 목록에 노출됩니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "승인 요청에 실패했습니다.");
    }
  }

  if (!user || !accessToken) {
    return (
      <main className="dashboard-layout">
        <Sidebar />
        <section className="dashboard-main">
          <EmptyState title="로그인이 필요합니다" body="레슨 프로필을 관리하려면 먼저 로그인해주세요." />
        </section>
      </main>
    );
  }

  return (
    <main className="dashboard-layout">
      <Sidebar />
      <section className="dashboard-main">
        <header className="admin-title">
          <div>
            <BackButton />
            <Badge tone={canSubmit ? "green" : "orange"}>{completion}/7 필수 항목 완료</Badge>
            <h1>레슨 프로필 관리</h1>
            <p>학생이 튜터를 선택할 때 보는 공개 정보를 작성합니다. 미디어 자료는 선택 사항입니다.</p>
          </div>
          <button>{statusLabel(profile?.status ?? user.tutorProfileStatus)}</button>
        </header>

        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}

        {!profile ? (
          <section className="panel setup-card tutor-setup-card">
            <div className="prep-icon">
              <UserRound size={24} />
            </div>
            <div>
              <h2>튜터 센터 시작</h2>
              <p>먼저 계정을 튜터 모드로 전환하면 프로필 작성, 일정 등록, 승인 요청을 진행할 수 있습니다.</p>
            </div>
            <button className="primary-button" onClick={() => void switchTutor()} disabled={saving}>
              튜터로 전환
            </button>
          </section>
        ) : (
          <section className="profile-workspace">
            <form className="panel profile-form tutor-profile-form" onSubmit={save}>
              <div className="form-section-head">
                <span>기본 정보</span>
                <strong>필수</strong>
              </div>
              <Field label="표시 이름" hint="예: 김지현 튜터" error={visibleErrors.displayName}>
                <input name="displayName" value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} />
              </Field>
              <Field label="짧은 소개" hint="튜터 카드에 노출됩니다. 예: TOPIK과 회화를 함께 잡는 수업" error={visibleErrors.shortIntroduction}>
                <input name="shortIntroduction" value={form.shortIntroduction} onChange={(event) => setForm({ ...form, shortIntroduction: event.target.value })} />
              </Field>
              <div className="form-two-col">
                <Field label="카테고리">
                  <select name="category" value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value as TutorCategory })}>
                    {categoryOptions.map(([value, label]) => (
                      <option value={value} key={value}>{label}</option>
                    ))}
                  </select>
                </Field>
                <Field label="가능 언어" hint="쉼표로 구분합니다. 예: 한국어, 영어" error={visibleErrors.availableLanguages}>
                  <input
                    name="availableLanguages"
                    value={form.availableLanguages.join(", ")}
                    onChange={(event) =>
                      setForm({ ...form, availableLanguages: event.target.value.split(",").map((item) => item.trim()).filter(Boolean) })
                    }
                  />
                </Field>
              </div>
              <div className="form-two-col">
                <Field label="25분 가격" hint="USD 기준" error={visibleErrors.lessonPrice25Amount}>
                  <input
                    name="lessonPrice25Amount"
                    type="number"
                    min="1"
                    value={form.lessonPrice25Amount}
                    onChange={(event) => setForm({ ...form, lessonPrice25Amount: Number(event.target.value) })}
                  />
                </Field>
                <Field label="50분 가격" hint="USD 기준" error={visibleErrors.lessonPrice50Amount}>
                  <input
                    name="lessonPrice50Amount"
                    type="number"
                    min="1"
                    value={form.lessonPrice50Amount}
                    onChange={(event) => setForm({ ...form, lessonPrice50Amount: Number(event.target.value) })}
                  />
                </Field>
              </div>

              <div className="form-section-head">
                <span>미디어</span>
                <strong className="optional">선택</strong>
              </div>
              <div className="optional-media-grid">
                <Field label="프로필 이미지" hint="비워두면 기본 이미지가 표시됩니다." error={visibleErrors.profileImageUrl}>
                  <label className="file-drop">
                    <ImagePlus size={18} />
                    <span>{uploadingImageField === "profileImageUrl" ? "업로드 중..." : "이미지 선택"}</span>
                    <input
                      name="profileImageUrl"
                      type="file"
                      accept="image/jpeg,image/png,image/webp,image/gif"
                      disabled={uploadingImageField === "profileImageUrl"}
                      onChange={(event) => void uploadImage("profileImageUrl", event.target.files?.[0] ?? null)}
                    />
                  </label>
                  {form.profileImageUrl ? (
                    <div className="image-upload-preview">
                      <img src={resolveAssetUrl(form.profileImageUrl) ?? "/images/default-tutor-profile.png"} alt="프로필 이미지 미리보기" />
                      <button type="button" className="ghost-button" onClick={() => setForm({ ...form, profileImageUrl: "" })}>삭제</button>
                    </div>
                  ) : null}
                </Field>
                <Field label="썸네일 이미지" hint="목록용 이미지를 따로 쓰고 싶을 때만 추가하세요." error={visibleErrors.thumbnailUrl}>
                  <label className="file-drop">
                    <ImagePlus size={18} />
                    <span>{uploadingImageField === "thumbnailUrl" ? "업로드 중..." : "썸네일 선택"}</span>
                    <input
                      name="thumbnailUrl"
                      type="file"
                      accept="image/jpeg,image/png,image/webp,image/gif"
                      disabled={uploadingImageField === "thumbnailUrl"}
                      onChange={(event) => void uploadImage("thumbnailUrl", event.target.files?.[0] ?? null)}
                    />
                  </label>
                  {form.thumbnailUrl ? (
                    <div className="image-upload-preview">
                      <img src={resolveAssetUrl(form.thumbnailUrl) ?? "/images/default-tutor-profile.png"} alt="썸네일 이미지 미리보기" />
                      <button type="button" className="ghost-button" onClick={() => setForm({ ...form, thumbnailUrl: "" })}>삭제</button>
                    </div>
                  ) : null}
                </Field>
              </div>
              <Field label="소개 영상 URL" hint="YouTube 링크를 넣으면 프로필 상단에서 미리보기로 보여줍니다." error={visibleErrors.introVideoUrl}>
                <div className="url-input">
                  <Link2 size={16} />
                  <input name="introVideoUrl" value={form.introVideoUrl} onChange={(event) => setForm({ ...form, introVideoUrl: event.target.value })} placeholder="https://youtu.be/..." />
                </div>
                {introVideoEmbedUrl ? (
                  <div className="video-preview">
                    <iframe
                      src={introVideoEmbedUrl}
                      title="소개 영상 미리보기"
                      loading="lazy"
                      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                      allowFullScreen
                    />
                  </div>
                ) : null}
              </Field>

              <div className="form-section-head">
                <span>수업 소개</span>
                <strong>필수</strong>
              </div>
              <Field label="About me" hint="경력, 수업 방식, 어떤 학생에게 적합한지 적어주세요." error={visibleErrors.aboutMe}>
                <textarea name="aboutMe" value={form.aboutMe} onChange={(event) => setForm({ ...form, aboutMe: event.target.value })} />
              </Field>
              <Field label="What I Offer" hint="제공 수업을 줄바꿈 또는 쉼표로 구분하면 상세 화면에서 읽기 좋습니다." error={visibleErrors.whatIOffer}>
                <textarea name="whatIOffer" value={form.whatIOffer} onChange={(event) => setForm({ ...form, whatIOffer: event.target.value })} />
              </Field>

              <div className="form-section-head">
                <span>운영 정보</span>
                <strong className="optional">선택</strong>
              </div>
              <Field label="가능 시간 메모" hint="예: 평일 저녁, 주말 오전 위주">
                <input name="availableTimeNote" value={form.availableTimeNote} onChange={(event) => setForm({ ...form, availableTimeNote: event.target.value })} />
              </Field>
              <Field label="정산 수단" hint="승인 후 정산 전에 추가해도 됩니다. 예: PayPal email@example.com">
                <input name="paymentMethod" value={form.paymentMethod} onChange={(event) => setForm({ ...form, paymentMethod: event.target.value })} />
              </Field>
              <div className="button-row sticky-form-actions">
                <button className="primary-button" disabled={saving}>{saving ? "저장 중..." : "임시저장"}</button>
                <button className="ghost-button" type="button" onClick={() => void submitProfile()} disabled={saving}>
                  <Send size={16} /> 승인 요청
                </button>
                <a className="ghost-button" href="/tutor/schedule">일정 관리</a>
              </div>
            </form>

            <aside className="panel profile-guide tutor-profile-guide">
              <h2>등록 진행</h2>
              <div className="progress-track">
                <span style={{ width: `${(completion / 7) * 100}%` }} />
              </div>
              <div className="profile-checklist">
                {[
                  ["표시 이름", Boolean(form.displayName.trim())],
                  ["짧은 소개", Boolean(form.shortIntroduction.trim())],
                  ["자기소개", Boolean(form.aboutMe.trim())],
                  ["수업 내용", Boolean(form.whatIOffer.trim())],
                  ["가능 언어", form.availableLanguages.length > 0],
                  ["25분 가격", form.lessonPrice25Amount > 0],
                  ["50분 가격", form.lessonPrice50Amount > 0]
                ].map(([label, done]) => (
                  <p className={done ? "done" : ""} key={String(label)}>
                    {done ? <CheckCircle2 size={17} /> : <Circle size={17} />}
                    <span>{label}</span>
                  </p>
                ))}
              </div>
              <div className="guide-callout">
                <strong>선택 항목</strong>
                <p>프로필 이미지, 썸네일, 소개 영상, 가능 시간 메모, 정산 수단은 승인 요청을 막지 않습니다.</p>
              </div>
              <div className="guide-callout">
                <strong>다음 단계</strong>
                <p>승인 후에는 일정 관리에서 수업 가능한 시간을 등록해야 학생이 예약할 수 있습니다.</p>
              </div>
            </aside>
          </section>
        )}
      </section>
    </main>
  );
}
