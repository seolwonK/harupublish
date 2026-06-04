"use client";

import {
  ArrowLeft,
  Banknote,
  Bell,
  CalendarDays,
  CheckCircle2,
  Clock,
  CreditCard,
  Home,
  LayoutDashboard,
  Loader2,
  MessageCircle,
  Search,
  Send,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  Star,
  Users,
  Video,
  Wallet
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { ExpertListResponse, Role } from "./api";
import { haruApi, resolveAssetUrl, toMoney } from "./api";
import { useAuth } from "./auth";
import { useCurrency } from "./currency";
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
    return href?.startsWith("/") ? (
      <Link className={classes} href={href}>
        {children}
      </Link>
    ) : (
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
      <img src="/images/haru-logo-cropped.png" alt="Haru" width={90} height={36} decoding="async" />
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
      {src ? <img src={src} alt="" width={48} height={48} loading="lazy" decoding="async" onError={() => setBroken(true)} /> : <span>{label}</span>}
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
      width={large ? 520 : 320}
      height={large ? 520 : 320}
      loading={large ? "eager" : "lazy"}
      decoding="async"
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

function roleLabel(role: Role) {
  if (role === "TUTOR") return "튜터 모드";
  if (role === "ADMIN") return "관리자 모드";
  return "학생 모드";
}

function roleHomePath(role: Role) {
  if (role === "TUTOR") return "/tutor/dashboard";
  if (role === "ADMIN") return "/admin";
  return "/";
}

function isNavSelected(pathname: string, href: string) {
  return pathname === href || (href !== "/" && pathname.startsWith(`${href}/`));
}

export function RoleModeSwitcher({ compact = false }: { compact?: boolean }) {
  const router = useRouter();
  const { user, accessToken, refreshMe } = useAuth();
  const [loadingRole, setLoadingRole] = useState<Role | null>(null);

  if (!user || !accessToken || user.roles.length <= 1) {
    return null;
  }

  const currentUser = user;
  const token = accessToken;

  async function switchRole(role: Role) {
    if (role === currentUser.activeRole) {
      router.push(roleHomePath(role));
      return;
    }

    setLoadingRole(role);
    try {
      await haruApi.changeActiveRole(token, role);
      await refreshMe();
      router.push(roleHomePath(role));
    } finally {
      setLoadingRole(null);
    }
  }

  return (
    <div className={cn("mode-switcher", compact && "mode-switcher-compact")} aria-label="모드 전환">
      {currentUser.roles.map((role) => (
        <button
          className={cn("mode-switch-button", currentUser.activeRole === role && "selected")}
          disabled={loadingRole !== null}
          key={role}
          onClick={() => void switchRole(role)}
          type="button"
        >
          {loadingRole === role ? <Loader2 size={14} className="spin-icon" /> : null}
          <span>{roleLabel(role)}</span>
        </button>
      ))}
    </div>
  );
}

export function CurrencyToggle() {
  const { displayCurrency, fxRate, setDisplayCurrency } = useCurrency();
  const krwDisabled = fxRate === null;

  return (
    <div className="currency-toggle" role="group" aria-label="표시 통화 전환">
      <button
        type="button"
        className={cn("currency-toggle-button", displayCurrency === "USD" && "selected")}
        onClick={() => setDisplayCurrency("USD")}
        aria-pressed={displayCurrency === "USD"}
      >
        USD
      </button>
      <button
        type="button"
        className={cn("currency-toggle-button", displayCurrency === "KRW" && "selected")}
        onClick={() => setDisplayCurrency("KRW")}
        aria-pressed={displayCurrency === "KRW"}
        disabled={krwDisabled}
        title={krwDisabled ? "환율 정보를 불러오지 못해 USD로만 표시합니다." : undefined}
      >
        KRW
      </button>
    </div>
  );
}

const CHAT_UNREAD_POLL_MS = 30_000;

/** 네비게이션 배지용 전체 미읽음 채팅 수. 30초 간격으로 폴링한다. */
function useChatUnreadCount() {
  const { accessToken } = useAuth();
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!accessToken) {
      setCount(0);
      return;
    }
    let cancelled = false;
    const load = () => {
      haruApi
        .getChatUnreadCount(accessToken)
        .then((response) => {
          if (!cancelled) setCount(response.count);
        })
        .catch(() => undefined);
    };
    load();
    const intervalId = setInterval(load, CHAT_UNREAD_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(intervalId);
    };
  }, [accessToken]);

  return count;
}

export function AppHeader() {
  const { user, logout } = useAuth();
  const pathname = usePathname();
  const chatUnreadCount = useChatUnreadCount();
  const isAdmin = user?.roles.includes("ADMIN");
  const navItems = user?.activeRole === "TUTOR"
    ? [
        ["튜터 센터", "/tutor/dashboard"],
        ["내 수업", "/bookings"],
        ["일정 관리", "/tutor/schedule"],
        ["레슨 프로필", "/tutor/profile"],
        ["채팅", "/chat"]
      ]
    : user?.activeRole === "ADMIN"
      ? [
          ["관리자 홈", "/admin"],
          ["튜터 승인", "/admin/tutors"],
          ["채팅", "/chat"],
          ["계정", "/account"]
        ]
      : [
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
          <a className={isNavSelected(pathname, href) ? "selected" : ""} href={href} key={href} aria-current={isNavSelected(pathname, href) ? "page" : undefined}>
            {label}
            {href === "/chat" && chatUnreadCount > 0 ? <b className="nav-unread-badge">{chatUnreadCount > 99 ? "99+" : chatUnreadCount}</b> : null}
          </a>
        ))}
      </nav>
      <div className="header-actions">
        <CurrencyToggle />
        {user ? (
          <>
            <RoleModeSwitcher compact />
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
    ["수수료 설정", SlidersHorizontal, "/admin/settings"],
    ["인출 관리", Banknote, "/admin/withdrawals"],
    ["신고/후기", Bell, "/admin"],
    ["통계", LayoutDashboard, "/admin"]
  ];
  const tutorItems: Array<[string, IconType, string]> = [
    ["홈", Home, "/tutor/dashboard"],
    ["내 수업", Video, "/bookings"],
    ["일정 관리", CalendarDays, "/tutor/schedule"],
    ["레슨 프로필", Users, "/tutor/profile"],
    ["채팅", MessageCircle, "/chat"],
    ["수익 관리", Wallet, "/tutor/earnings"],
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
          const selected = isNavSelected(pathname, href) && (href !== (admin ? "/admin" : "/tutor/dashboard") || index === 0 || pathname === href);
          return (
            <a className={selected ? "selected" : ""} href={href} key={`${label}-${index}`}>
              <Icon size={17} />
              <span>{label}</span>
            </a>
          );
        })}
      </div>
      {!admin ? <RoleModeSwitcher /> : null}
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
    REFUND_REQUESTED: "환불 요청",
    REQUESTED: "요청됨",
    REVERSED: "취소(환원)",
    OPEN: "집계 중",
    CLOSED: "마감",
    FINALIZED: "확정",
    NO_SHOW: "노쇼"
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

export function Footer() {
  const year = new Date().getFullYear();
  return (
    <footer className="site-footer">
      <div className="site-footer-inner">
        <div className="site-footer-brand">
          <BrandLogo />
          <p>실시간 한국어 · K-pop · K-beauty · 라이프스타일 레슨을 연결하는 Haru 플랫폼.</p>
        </div>
        <nav className="site-footer-links" aria-label="법적 고지">
          <Link href="/terms">이용약관</Link>
          <Link href="/privacy">개인정보처리방침</Link>
          <a href="mailto:bridgoworld@gmail.com">문의하기</a>
        </nav>
      </div>
      <div className="site-footer-bottom">
        <span>© {year} Haru. All rights reserved.</span>
        <span>Contact: bridgoworld@gmail.com</span>
      </div>

      <style>{`
        .site-footer {
          width: min(1180px, calc(100vw - 40px));
          margin: 40px auto 24px;
          padding: 32px clamp(20px, 4vw, 40px) 20px;
          background: var(--panel);
          border: 1px solid var(--line);
          border-radius: 18px;
          box-shadow: var(--shadow-soft);
        }
        .site-footer-inner {
          display: flex;
          flex-wrap: wrap;
          gap: 28px;
          justify-content: space-between;
          align-items: flex-start;
        }
        .site-footer-brand {
          max-width: 360px;
        }
        .site-footer-brand p {
          margin: 14px 0 0;
          color: var(--muted);
          font-size: 14px;
          line-height: 1.7;
        }
        .site-footer-links {
          display: flex;
          flex-wrap: wrap;
          gap: 18px 26px;
          align-items: center;
        }
        .site-footer-links a {
          color: var(--ink);
          font-size: 14px;
          font-weight: 600;
          transition: color 160ms ease;
        }
        .site-footer-links a:hover {
          color: var(--brand-dark);
        }
        .site-footer-bottom {
          display: flex;
          flex-wrap: wrap;
          gap: 8px 20px;
          justify-content: space-between;
          margin-top: 26px;
          padding-top: 18px;
          border-top: 1px solid var(--line);
          color: var(--muted);
          font-size: 13px;
        }
        @media (max-width: 640px) {
          .site-footer-inner {
            flex-direction: column;
            gap: 22px;
          }
        }
      `}</style>
    </footer>
  );
}

export function LegalStyles() {
  return (
    <style>{`
      .legal-doc {
        width: min(880px, 100%);
        margin: 28px auto 0;
        background: var(--panel);
        border: 1px solid var(--line);
        border-radius: 18px;
        box-shadow: var(--shadow-soft);
        padding: clamp(28px, 5vw, 56px);
      }
      .legal-head {
        padding-bottom: 28px;
        margin-bottom: 8px;
        border-bottom: 1px solid var(--line);
      }
      .legal-eyebrow {
        display: inline-block;
        font-size: 13px;
        font-weight: 700;
        letter-spacing: 0.14em;
        text-transform: uppercase;
        color: var(--brand-dark);
        background: var(--brand-soft);
        padding: 6px 14px;
        border-radius: 999px;
        margin-bottom: 18px;
      }
      .legal-head h1 {
        margin: 0 0 10px;
        font-size: clamp(26px, 4vw, 34px);
        line-height: 1.2;
        letter-spacing: -0.01em;
        color: var(--ink);
      }
      .legal-meta {
        margin: 0;
        color: var(--muted);
        font-size: 14px;
        font-weight: 500;
      }
      .legal-body {
        display: flex;
        flex-direction: column;
        gap: 34px;
        padding-top: 32px;
      }
      .legal-article {
        scroll-margin-top: 90px;
      }
      .legal-article-title {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        gap: 12px;
        margin: 0 0 14px;
        font-size: clamp(17px, 2.4vw, 20px);
        line-height: 1.35;
        color: var(--ink);
      }
      .legal-article-num {
        font-size: 13px;
        font-weight: 800;
        letter-spacing: 0.04em;
        color: #ffffff;
        background: var(--brand);
        padding: 5px 12px;
        border-radius: 999px;
        white-space: nowrap;
        box-shadow: 0 6px 16px var(--brand-shadow);
      }
      .legal-article-body {
        color: #34373c;
        font-size: 15.5px;
        line-height: 1.85;
      }
      .legal-article-body p {
        margin: 0;
      }
      .legal-article-body strong {
        color: var(--brand-dark);
        font-weight: 700;
      }
      .legal-list {
        margin: 0;
        padding: 0;
        list-style: none;
        counter-reset: legal-counter;
        display: flex;
        flex-direction: column;
        gap: 14px;
      }
      .legal-list > li {
        counter-increment: legal-counter;
        position: relative;
        padding-left: 38px;
      }
      .legal-list > li::before {
        content: counter(legal-counter);
        position: absolute;
        left: 0;
        top: 1px;
        width: 26px;
        height: 26px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
        font-weight: 800;
        color: var(--brand-dark);
        background: var(--brand-soft);
        border-radius: 8px;
      }
      .legal-article-body a {
        color: var(--brand-dark);
        font-weight: 600;
        text-decoration: underline;
      }
      .legal-contact {
        margin-top: 36px;
        padding: 22px 24px;
        background: var(--brand-soft);
        border-radius: 14px;
        color: var(--brand-dark);
        font-size: 15px;
        line-height: 1.7;
      }
      .legal-contact strong {
        display: block;
        margin-bottom: 4px;
        font-size: 15px;
      }
      .legal-contact a {
        color: var(--brand-dark);
        font-weight: 700;
        text-decoration: underline;
      }
      @media (max-width: 640px) {
        .legal-article-body {
          font-size: 15px;
          line-height: 1.8;
        }
      }
    `}</style>
  );
}
