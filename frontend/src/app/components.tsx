"use client";

import {
  ArrowLeft,
  Bell,
  CalendarDays,
  CheckCircle2,
  Clock,
  CreditCard,
  Home,
  LayoutDashboard,
  MessageCircle,
  Search,
  Send,
  Settings,
  ShieldCheck,
  Star,
  Users,
  Wallet
} from "lucide-react";
import { usePathname } from "next/navigation";
import { useState } from "react";
import type { ExpertListResponse } from "./api";
import { resolveAssetUrl, toMoney } from "./api";
import { useAuth } from "./auth";
import type { Booking, Tutor } from "./data";

const DEFAULT_TUTOR_IMAGE = "/images/default-tutor-profile.png";

type IconType = typeof Home;

export function cn(...classes: Array<string | false | null | undefined>) {
  return classes.filter(Boolean).join(" ");
}

export function Button({
  as = "button",
  href,
  variant = "primary",
  className = "",
  children,
  ...props
}: {
  as?: "button" | "a";
  href?: string;
  variant?: "primary" | "secondary" | "ghost" | "link";
  className?: string;
  children: React.ReactNode;
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const classes = cn("ui-button", `ui-button-${variant}`, className);
  if (as === "a") {
    return (
      <a className={classes} href={href}>
        {children}
      </a>
    );
  }
  return (
    <button className={classes} {...props}>
      {children}
    </button>
  );
}

export function Card({ className = "", children }: { className?: string; children: React.ReactNode }) {
  return <section className={cn("ui-card", className)}>{children}</section>;
}

export function Badge({
  tone = "neutral",
  children,
  className = ""
}: {
  tone?: "brand" | "green" | "orange" | "red" | "blue" | "neutral";
  children: React.ReactNode;
  className?: string;
}) {
  return <span className={cn("ui-badge", `ui-badge-${tone}`, className)}>{children}</span>;
}

export function SectionHeader({
  eyebrow,
  title,
  description,
  action
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="section-header">
      <div>
        {eyebrow ? <span className="section-eyebrow">{eyebrow}</span> : null}
        <h2>{title}</h2>
        {description ? <p>{description}</p> : null}
      </div>
      {action ? <div className="section-action">{action}</div> : null}
    </div>
  );
}

function BrandLogo({ admin = false }: { admin?: boolean }) {
  return (
    <span className="brand-logo">
      <img src="/images/haru-logo-cropped.png" alt="Haru" />
      {admin ? <span>Admin</span> : null}
    </span>
  );
}

export function Avatar({
  label,
  large,
  className = "",
  imageUrl
}: {
  label: string;
  large?: boolean;
  className?: string;
  imageUrl?: string | null;
}) {
  const [broken, setBroken] = useState(false);
  const src = broken ? null : resolveAssetUrl(imageUrl);

  return (
    <div className={cn("avatar", large && "avatar-large", className)}>
      {src ? <img src={src} alt="" onError={() => setBroken(true)} /> : <span>{label}</span>}
    </div>
  );
}

export function TutorPortrait({ imageUrl, label, large = false }: { imageUrl?: string | null; label: string; large?: boolean }) {
  const [src, setSrc] = useState(resolveAssetUrl(imageUrl) ?? DEFAULT_TUTOR_IMAGE);

  return (
    <img
      className={large ? "tutor-image tutor-image-large" : "tutor-image"}
      src={src}
      alt={label ? `${label} 프로필 이미지` : ""}
      onError={() => {
        if (src !== DEFAULT_TUTOR_IMAGE) setSrc(DEFAULT_TUTOR_IMAGE);
      }}
    />
  );
}

export function Rating({ value, reviews }: { value: number; reviews?: number }) {
  return (
    <div className="rating">
      <Star size={14} fill="currentColor" />
      <span>{value.toFixed(1)}</span>
      {reviews ? <span className="muted">({reviews})</span> : null}
    </div>
  );
}

export function AppHeader() {
  const { user, logout } = useAuth();
  const pathname = usePathname();
  const isAdmin = user?.roles.includes("ADMIN");
  const navItems = [
    ["튜터 찾기", "/tutors"],
    ["내 예약", "/bookings"],
    ["결제", "/payments"],
    ["튜터 센터", "/tutor/dashboard"],
    ...(isAdmin ? [["관리자", "/admin"]] : []),
    ["채팅", "/chat"]
  ];

  return (
    <header className="app-header">
      <a className="brand" href="/" aria-label="Haru 홈">
        <BrandLogo />
      </a>
      <nav aria-label="주요 메뉴">
        {navItems.map(([label, href]) => (
          <a className={pathname === href ? "selected" : ""} href={href} key={href} aria-current={pathname === href ? "page" : undefined}>
            {label}
          </a>
        ))}
      </nav>
      <div className="header-actions">
        <div className="lang-toggle" aria-label="언어 선택">
          <span>KOR</span>
          <span>ENG</span>
        </div>
        {user ? (
          <>
            <a href="/account">{user.name}</a>
            <button onClick={() => void logout()}>로그아웃</button>
            <Bell size={18} aria-hidden />
            <Avatar label={user.name?.slice(0, 1) ?? "H"} />
          </>
        ) : (
          <>
            <a href="/login">로그인</a>
            <a className="header-signup" href="/signup">
              시작하기
            </a>
          </>
        )}
      </div>
    </header>
  );
}

export function Sidebar({ admin = false }: { admin?: boolean }) {
  const pathname = usePathname();
  const adminItems: Array<[string, IconType, string]> = [
    ["대시보드", LayoutDashboard, "/admin"],
    ["회원 관리", Users, "/admin"],
    ["튜터 승인", ShieldCheck, "/admin/tutors"],
    ["예약 관리", CalendarDays, "/admin"],
    ["결제/정산", CreditCard, "/admin"],
    ["신고/후기", Bell, "/admin"],
    ["통계", LayoutDashboard, "/admin"]
  ];
  const tutorItems: Array<[string, IconType, string]> = [
    ["홈", Home, "/tutor/dashboard"],
    ["일정 관리", CalendarDays, "/tutor/schedule"],
    ["레슨 프로필", Users, "/tutor/profile"],
    ["완료 수업", CheckCircle2, "/tutor/dashboard"],
    ["수익 관리", Wallet, "/tutor/dashboard"],
    ["설정", Settings, "/account"]
  ];
  const items = admin ? adminItems : tutorItems;

  return (
    <aside className="sidebar">
      <a className="brand" href="/">
        <BrandLogo admin={admin} />
      </a>
      <div className="side-links">
        {items.map(([label, Icon, href], index) => {
          const selected = pathname === href && (href !== (admin ? "/admin" : "/tutor/dashboard") || index === 0);
          return (
            <a className={selected ? "selected" : ""} href={href} key={`${label}-${index}`}>
              <Icon size={17} />
              <span>{label}</span>
            </a>
          );
        })}
      </div>
      {!admin ? (
        <a className="role-switch" href="/">
          학생 화면으로 이동
        </a>
      ) : null}
      <a className="logout" href="/account">
        계정 설정
      </a>
    </aside>
  );
}

export function BackButton({ label = "뒤로가기" }: { label?: string }) {
  return (
    <button className="back-button" type="button" onClick={() => window.history.back()}>
      <ArrowLeft size={16} />
      {label}
    </button>
  );
}

export function TutorCard({ tutor }: { tutor: Tutor }) {
  return (
    <article className="tutor-card">
      <div className={`portrait bg-gradient-to-br ${tutor.accent}`}>
        <TutorPortrait imageUrl={undefined} label={tutor.avatar} />
      </div>
      <div className="tutor-card-body">
        <Badge tone="brand">{tutor.subject}</Badge>
        <h3>{tutor.name}</h3>
        <Rating value={tutor.rating} reviews={tutor.reviews} />
        <p>{tutor.languages}</p>
        <strong>{tutor.price}</strong>
      </div>
      <Button as="a" href={`/tutors/${tutor.id}`} variant="secondary" className="wide">
        프로필 보기
      </Button>
    </article>
  );
}

export function ExpertCard({ tutor }: { tutor: ExpertListResponse }) {
  const imageUrl = tutor.thumbnailUrl || tutor.profileImageUrl;

  return (
    <article className="tutor-card">
      <div className="portrait">
        <TutorPortrait imageUrl={imageUrl} label={(tutor.displayName ?? "T").slice(0, 1)} />
      </div>
      <div className="tutor-card-body">
        <Badge tone="brand">{categoryLabel(tutor.category)}</Badge>
        <h3>{tutor.displayName ?? "Haru 튜터"}</h3>
        <p>{tutor.shortIntroduction ?? "한국어와 한국 문화를 함께 알려드려요."}</p>
        {(tutor.reviewCount ?? 0) > 0 ? <Rating value={tutor.averageRating ?? 0} reviews={tutor.reviewCount ?? 0} /> : <span className="muted">아직 리뷰 없음</span>}
        <span className="muted">{(tutor.availableLanguages ?? ["한국어"]).join(" · ")}</span>
        <strong>25분 {toMoney(tutor.lessonPrice25Amount)}</strong>
      </div>
      <Button as="a" href={`/tutors/${tutor.tutorProfileId}`} variant="secondary" className="wide">
        프로필 보기
      </Button>
    </article>
  );
}

export function LessonCard() {
  return (
    <article className="lesson-card">
      <div className="lesson-main">
        <Avatar label="JH" />
        <div>
          <strong>김지현 튜터와 한국어 회화</strong>
          <p>5월 22일 오전 10:00 · 50분</p>
          <Badge tone="green">수업 예정</Badge>
        </div>
      </div>
      <Button as="a" href="/bookings" variant="primary">
        입장 준비
      </Button>
    </article>
  );
}

export function BookingItem({ booking }: { booking: Booking }) {
  return (
    <article className="booking-item">
      <Avatar label={booking.avatar} />
      <div>
        <strong>{booking.tutor}</strong>
        <p>
          {booking.date} {booking.time} · {booking.duration}
        </p>
        <span className="muted">수업 자료와 Jitsi 링크가 예약 상세에 준비됩니다.</span>
      </div>
      <Badge tone={booking.status === "완료" ? "blue" : booking.status === "취소" ? "red" : "green"}>{booking.status}</Badge>
    </article>
  );
}

export function CalendarMock() {
  const days = Array.from({ length: 31 }, (_, i) => i + 1);
  return (
    <section className="panel calendar-panel">
      <div className="panel-head">
        <h2>2026년 5월</h2>
        <Button variant="ghost">오늘</Button>
      </div>
      <div className="calendar-grid weekdays">
        {["일", "월", "화", "수", "목", "금", "토"].map((day) => (
          <span key={day}>{day}</span>
        ))}
      </div>
      <div className="calendar-grid">
        {days.map((day) => (
          <span className={day === 22 ? "selected-day" : day === 19 ? "dot-day" : ""} key={day}>
            {day}
          </span>
        ))}
      </div>
    </section>
  );
}

export function StatCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <article className="stat-card">
      <p>{label}</p>
      <strong>{value}</strong>
      {hint ? <span>{hint}</span> : null}
    </article>
  );
}

export function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <p>{body}</p>
    </div>
  );
}

export function ApiNotice({ type = "info", children }: { type?: "info" | "error" | "success"; children: React.ReactNode }) {
  return <div className={`api-notice api-notice-${type}`}>{children}</div>;
}

export function Field({
  label,
  hint,
  error,
  children
}: {
  label: string;
  hint?: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <label className={`form-field ${error ? "form-field-error" : ""}`}>
      <span>{label}</span>
      {children}
      {hint ? <small>{hint}</small> : null}
      {error ? <small className="field-error">{error}</small> : null}
    </label>
  );
}

export const timeZoneOptions = [
  { value: "Asia/Seoul", label: "한국 - 서울 (Asia/Seoul)" },
  { value: "Asia/Tokyo", label: "일본 - 도쿄 (Asia/Tokyo)" },
  { value: "Asia/Shanghai", label: "중국 - 상하이 (Asia/Shanghai)" },
  { value: "Asia/Singapore", label: "싱가포르 (Asia/Singapore)" },
  { value: "America/Los_Angeles", label: "미국 - LA (America/Los_Angeles)" },
  { value: "America/New_York", label: "미국 - 뉴욕 (America/New_York)" },
  { value: "Europe/London", label: "영국 - 런던 (Europe/London)" },
  { value: "Europe/Paris", label: "프랑스 - 파리 (Europe/Paris)" },
  { value: "UTC", label: "UTC" }
];

export function TimeZoneSelect({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <select value={value} onChange={(event) => onChange(event.target.value)}>
      {timeZoneOptions.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
}

export function categoryLabel(category: string | null | undefined) {
  const labels: Record<string, string> = {
    KOREAN: "한국어 회화",
    KPOP: "K-POP",
    KBEAUTY: "K-뷰티",
    OTHER: "한국 문화"
  };
  return labels[category ?? ""] ?? "한국어";
}

export function statusLabel(status: string | null | undefined) {
  const labels: Record<string, string> = {
    DRAFT: "임시저장",
    PENDING: "승인 대기",
    APPROVED: "승인 완료",
    REJECTED: "반려",
    RESERVED: "예약",
    CANCELLED: "취소",
    COMPLETED: "완료",
    PAID: "결제 완료",
    FAILED: "실패",
    REFUNDED: "환불 완료",
    REFUND_REQUESTED: "환불 요청"
  };
  return labels[status ?? ""] ?? status ?? "-";
}

export function SearchBox() {
  return (
    <label className="search-box">
      <Search size={16} />
      <input placeholder="튜터, 주제, 언어 검색" />
    </label>
  );
}

export function SendButton() {
  return (
    <button className="send-button" aria-label="메시지 보내기">
      <Send size={19} />
    </button>
  );
}

export function TimePill({
  children,
  selected,
  onClick,
  disabled
}: {
  children: React.ReactNode;
  selected?: boolean;
  onClick?: () => void;
  disabled?: boolean;
}) {
  return (
    <button className={`time-pill ${selected ? "selected" : ""}`} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  );
}

export function IconMeta({ icon: Icon, children }: { icon: IconType; children: React.ReactNode }) {
  return (
    <span className="icon-meta">
      <Icon size={15} />
      {children}
    </span>
  );
}
