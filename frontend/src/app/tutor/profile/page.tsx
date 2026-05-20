"use client";

import { useEffect, useMemo, useState } from "react";
import { haruApi, resolveAssetUrl, TutorCategory, TutorProfileRequest, TutorProfileResponse } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, BackButton, EmptyState, Field, Sidebar, statusLabel } from "../../components";

type ProfileErrors = Partial<Record<keyof TutorProfileRequest, string>>;
type ValidationMode = "save" | "submit";

const requiredSubmitFields: (keyof TutorProfileRequest)[] = [
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

function isOptionalUrl(value: string) {
  if (!value.trim()) return true;
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function validateProfile(form: TutorProfileRequest) {
  const errors: ProfileErrors = {};

  if (!form.displayName.trim()) errors.displayName = "학생에게 보일 튜터 이름을 입력해주세요.";
  if (form.displayName.length > 100) errors.displayName = "표시 이름은 100자 이하로 입력해주세요.";
  if (!form.shortIntroduction.trim()) errors.shortIntroduction = "카드에 노출될 한 줄 소개를 입력해주세요.";
  if (form.shortIntroduction.length > 255) errors.shortIntroduction = "짧은 소개는 255자 이하로 입력해주세요.";
  if (!form.aboutMe.trim() || form.aboutMe.trim().length < 30) errors.aboutMe = "자기소개는 최소 30자 이상 작성해주세요.";
  if (!form.whatIOffer.trim()) errors.whatIOffer = "제공 가능한 수업 유형을 입력해주세요.";
  if (form.availableLanguages.length === 0) errors.availableLanguages = "가능 언어를 1개 이상 입력해주세요.";
  if (!form.lessonPrice25Amount || form.lessonPrice25Amount < 1) errors.lessonPrice25Amount = "25분 수업 가격은 1 이상이어야 합니다.";
  if (!form.lessonPrice50Amount || form.lessonPrice50Amount < 1) errors.lessonPrice50Amount = "50분 수업 가격은 1 이상이어야 합니다.";
  if (form.lessonPrice50Amount < form.lessonPrice25Amount) errors.lessonPrice50Amount = "50분 가격은 25분 가격보다 낮을 수 없습니다.";
  if (!isOptionalUrl(form.profileImageUrl)) errors.profileImageUrl = "http 또는 https로 시작하는 이미지 URL을 입력해주세요.";
  if (!isOptionalUrl(form.thumbnailUrl)) errors.thumbnailUrl = "http 또는 https로 시작하는 썸네일 URL을 입력해주세요.";
  if (!isOptionalUrl(form.introVideoUrl)) errors.introVideoUrl = "http 또는 https로 시작하는 영상 URL을 입력해주세요.";

  return errors;
}

function validateProfileRelaxed(form: TutorProfileRequest, mode: ValidationMode) {
  const errors: ProfileErrors = {};

  if (mode === "submit" && !form.displayName.trim()) errors.displayName = "튜터 이름을 입력해주세요.";
  if (form.displayName.length > 100) errors.displayName = "표시 이름은 100자 이하로 입력해주세요.";
  if (mode === "submit" && !form.shortIntroduction.trim()) errors.shortIntroduction = "짧은 소개를 입력해주세요.";
  if (form.shortIntroduction.length > 255) errors.shortIntroduction = "짧은 소개는 255자 이하로 입력해주세요.";
  if (mode === "submit" && !form.aboutMe.trim()) errors.aboutMe = "자기소개를 입력해주세요.";
  if (mode === "submit" && !form.whatIOffer.trim()) errors.whatIOffer = "제공할 수업 내용을 입력해주세요.";
  if (mode === "submit" && form.availableLanguages.length === 0) errors.availableLanguages = "가능 언어를 1개 이상 입력해주세요.";
  if (!form.lessonPrice25Amount || form.lessonPrice25Amount < 1) errors.lessonPrice25Amount = "25분 수업 가격은 1 이상이어야 합니다.";
  if (!form.lessonPrice50Amount || form.lessonPrice50Amount < 1) errors.lessonPrice50Amount = "50분 수업 가격은 1 이상이어야 합니다.";
  if (!isOptionalUrl(form.introVideoUrl)) errors.introVideoUrl = "영상 링크는 http 또는 https 주소로 입력해주세요.";

  return errors;
}

function focusFirstError(errors: ProfileErrors) {
  const firstField = requiredSubmitFields.find((field) => errors[field]) ?? (Object.keys(errors)[0] as keyof TutorProfileRequest | undefined);
  if (!firstField) return;

  const target = document.querySelector<HTMLElement>(`[name="${firstField}"]`);
  target?.scrollIntoView({ behavior: "smooth", block: "center" });
  target?.focus({ preventScroll: true });
}

export default function TutorProfileManagePage() {
  const { user, accessToken, refreshMe } = useAuth();
  const [profile, setProfile] = useState<TutorProfileResponse | null>(null);
  const [form, setForm] = useState<TutorProfileRequest>(emptyProfile);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [validationMode, setValidationMode] = useState<ValidationMode>("save");
  const [uploadingImageField, setUploadingImageField] = useState<"profileImageUrl" | "thumbnailUrl" | null>(null);

  const errors = useMemo(() => validateProfileRelaxed(form, "save"), [form]);
  const hasErrors = Object.keys(errors).length > 0;

  useEffect(() => {
    if (!accessToken) return;
    haruApi
      .getMyTutorProfile(accessToken)
      .then((nextProfile) => {
        setProfile(nextProfile);
        setForm({
          displayName: nextProfile.displayName ?? "",
          shortIntroduction: nextProfile.shortIntroduction ?? "",
          aboutMe: nextProfile.aboutMe ?? "",
          whatIOffer: nextProfile.whatIOffer ?? emptyProfile.whatIOffer,
          category: nextProfile.category ?? "KOREAN",
          profileImageUrl: nextProfile.profileImageUrl ?? "",
          introVideoUrl: nextProfile.introVideoUrl ?? "",
          thumbnailUrl: nextProfile.thumbnailUrl ?? "",
          availableLanguages: nextProfile.availableLanguages ?? ["한국어"],
          lessonPrice25Amount: Number(nextProfile.lessonPrice25Amount ?? 13),
          lessonPrice50Amount: Number(nextProfile.lessonPrice50Amount ?? 25),
          availableTimeNote: nextProfile.availableTimeNote ?? "",
          paymentMethod: nextProfile.paymentMethod ?? ""
        });
      })
      .catch(() => undefined);
  }, [accessToken]);

  async function switchTutor() {
    if (!accessToken) return;
    setError(null);
    setMessage(null);
    try {
      const nextProfile = await haruApi.switchToTutor(accessToken);
      setProfile(nextProfile);
      await refreshMe();
      setMessage("튜터 모드가 활성화되었습니다. 이제 레슨 프로필을 작성할 수 있습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "튜터 전환에 실패했습니다.");
    }
  }

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (!accessToken) return;
    setValidationMode("save");
    setSubmitAttempted(true);
    setError(null);
    setMessage(null);
    if (hasErrors) {
      focusFirstError(errors);
      setError("입력값을 확인해주세요. 각 항목 아래에 수정 방법을 표시했습니다.");
      return;
    }
    try {
      const nextProfile = await haruApi.updateMyTutorProfile(accessToken, form);
      setProfile(nextProfile);
      await refreshMe();
      setMessage("레슨 프로필을 저장했습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로필 저장에 실패했습니다.");
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
      setMessage("이미지를 업로드하고 프로필에 바로 반영했습니다.");
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
    const submitErrors = validateProfileRelaxed(form, "submit");
    if (Object.keys(submitErrors).length > 0) {
      focusFirstError(submitErrors);
      setError("승인 요청 전에 필수 항목을 완성해주세요.");
      return;
    }
    try {
      const nextProfile = await haruApi.submitMyTutorProfile(accessToken);
      setProfile(nextProfile);
      await refreshMe();
      setMessage("승인 요청을 제출했습니다. 관리자가 검토하면 공개 튜터 목록에 노출됩니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "승인 요청에 실패했습니다.");
    }
  }

  const visibleErrors = submitAttempted ? validateProfileRelaxed(form, validationMode) : errors;

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
            <h1>레슨 프로필</h1>
            <p>학생이 튜터를 선택할 때 보는 공개 정보를 작성합니다.</p>
          </div>
          <button>{statusLabel(profile?.status ?? user.tutorProfileStatus)}</button>
        </header>

        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}

        {!profile ? (
          <section className="panel setup-card">
            <div>
              <h2>튜터 모드 시작</h2>
              <p>레슨 프로필을 만들려면 먼저 계정을 튜터 모드로 전환해야 합니다.</p>
            </div>
            <button className="primary-button" onClick={() => void switchTutor()}>
              튜터로 전환
            </button>
          </section>
        ) : null}

        <section className="profile-workspace">
          <form className="panel profile-form" onSubmit={save}>
            <Field label="표시 이름" hint="예: 김지현 튜터" error={visibleErrors.displayName}>
              <input name="displayName" value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} />
            </Field>
            <Field label="짧은 소개" hint="튜터 카드에 노출됩니다. 예: TOPIK과 회화를 함께 잡는 수업" error={visibleErrors.shortIntroduction}>
              <input name="shortIntroduction" value={form.shortIntroduction} onChange={(event) => setForm({ ...form, shortIntroduction: event.target.value })} />
            </Field>
            <Field label="카테고리">
              <select name="category" value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value as TutorCategory })}>
                <option value="KOREAN">한국어</option>
                <option value="KPOP">K-POP</option>
                <option value="KBEAUTY">K-뷰티</option>
                <option value="OTHER">기타</option>
              </select>
            </Field>
            <Field label="가능 언어" hint="쉼표로 구분합니다. 예: 한국어, 영어, 일본어" error={visibleErrors.availableLanguages}>
              <input
                name="availableLanguages"
                value={form.availableLanguages.join(", ")}
                onChange={(event) =>
                  setForm({ ...form, availableLanguages: event.target.value.split(",").map((item) => item.trim()).filter(Boolean) })
                }
              />
            </Field>
            <div className="form-two-col">
              <Field label="25분 가격" hint="USD 기준" error={visibleErrors.lessonPrice25Amount}>
                <input name="lessonPrice25Amount" type="number" min="1" value={form.lessonPrice25Amount} onChange={(event) => setForm({ ...form, lessonPrice25Amount: Number(event.target.value) })} />
              </Field>
              <Field label="50분 가격" hint="USD 기준" error={visibleErrors.lessonPrice50Amount}>
                <input name="lessonPrice50Amount" type="number" min="1" value={form.lessonPrice50Amount} onChange={(event) => setForm({ ...form, lessonPrice50Amount: Number(event.target.value) })} />
              </Field>
            </div>
            <Field label="프로필 이미지" hint="학생 카드와 프로필 상단에 사용할 JPG, PNG, WebP, GIF 파일을 첨부해주세요." error={visibleErrors.profileImageUrl}>
              <input
                name="profileImageUrl"
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                disabled={uploadingImageField === "profileImageUrl"}
                onChange={(event) => void uploadImage("profileImageUrl", event.target.files?.[0] ?? null)}
              />
              {form.profileImageUrl ? (
                <div className="image-upload-preview">
                  <img src={resolveAssetUrl(form.profileImageUrl) ?? "/images/default-tutor-profile.png"} alt="프로필 이미지 미리보기" />
                  <button type="button" className="ghost-button" onClick={() => setForm({ ...form, profileImageUrl: "" })}>
                    삭제
                  </button>
                </div>
              ) : null}
            </Field>
            <Field label="인트로 영상 URL" hint="튜터 소개 영상 링크입니다. YouTube URL도 사용할 수 있습니다." error={visibleErrors.introVideoUrl}>
              <input name="introVideoUrl" value={form.introVideoUrl} onChange={(event) => setForm({ ...form, introVideoUrl: event.target.value })} placeholder="https://example.com/video" />
            </Field>
            <Field label="썸네일 이미지" hint="영상 또는 프로필의 작은 미리보기로 사용할 이미지 파일을 첨부해주세요." error={visibleErrors.thumbnailUrl}>
              <input
                name="thumbnailUrl"
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                disabled={uploadingImageField === "thumbnailUrl"}
                onChange={(event) => void uploadImage("thumbnailUrl", event.target.files?.[0] ?? null)}
              />
              {form.thumbnailUrl ? (
                <div className="image-upload-preview">
                  <img src={resolveAssetUrl(form.thumbnailUrl) ?? "/images/default-tutor-profile.png"} alt="썸네일 이미지 미리보기" />
                  <button type="button" className="ghost-button" onClick={() => setForm({ ...form, thumbnailUrl: "" })}>
                    삭제
                  </button>
                </div>
              ) : null}
            </Field>
            <Field label="About me" hint="경력, 수업 방식, 어떤 학생에게 적합한지 30자 이상 적어주세요." error={visibleErrors.aboutMe}>
              <textarea name="aboutMe" value={form.aboutMe} onChange={(event) => setForm({ ...form, aboutMe: event.target.value })} />
            </Field>
            <Field label="What I Offer" hint="제공 수업을 쉼표로 구분하면 상세 화면에서 태그처럼 보입니다." error={visibleErrors.whatIOffer}>
              <textarea name="whatIOffer" value={form.whatIOffer} onChange={(event) => setForm({ ...form, whatIOffer: event.target.value })} />
            </Field>
            <Field label="가능 시간 메모" hint="예: 평일 저녁, 주말 오전 위주">
              <input name="availableTimeNote" value={form.availableTimeNote} onChange={(event) => setForm({ ...form, availableTimeNote: event.target.value })} />
            </Field>
            <Field label="정산 수단" hint="예: PayPal email@example.com">
              <input name="paymentMethod" value={form.paymentMethod} onChange={(event) => setForm({ ...form, paymentMethod: event.target.value })} />
            </Field>
            <div className="button-row">
              <button className="primary-button">저장</button>
              <button className="ghost-button" type="button" onClick={() => void submitProfile()}>
                승인 요청
              </button>
              <a className="ghost-button" href="/tutor/schedule">
                스케줄 관리
              </a>
            </div>
          </form>

          <aside className="panel profile-guide">
            <h2>작성 가이드</h2>
            <div>
              <strong>좋은 짧은 소개</strong>
              <p>“회화 자신감을 만드는 25분 한국어 루틴”처럼 수업 결과가 보이게 작성하세요.</p>
            </div>
            <div>
              <strong>미디어 URL</strong>
              <p>이미지와 영상은 선택 항목입니다. 비워도 저장할 수 있지만 입력한다면 http 또는 https 주소여야 합니다.</p>
            </div>
            <div>
              <strong>승인 전 체크</strong>
              <p>이름, 소개, 언어, 가격, 자기소개, 제공 수업이 모두 있어야 승인 요청을 보낼 수 있습니다.</p>
            </div>
          </aside>
        </section>
      </section>
    </main>
  );
}
